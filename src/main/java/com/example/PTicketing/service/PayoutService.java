package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.PayoutRequest;
import com.example.PTicketing.dto.response.BalanceResponse;
import com.example.PTicketing.dto.response.PayoutResponse;
import com.example.PTicketing.entity.*;
import com.example.PTicketing.enums.PaymentStatus;
import com.example.PTicketing.enums.PayoutStatus;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PaystackService paystackService;

    /** How long after an event ends the organiser's share becomes withdrawable. */
    @Value("${integration.payout-hold-hours:24}")
    private int payoutHoldHours;

    /** The provider's bank list, for the organiser to pick a code from. */
    public JsonNode listBanks() {
        return paystackService.listBanks();
    }

    @Transactional
    public PayoutResponse requestPayout(PayoutRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BalanceResponse balance = getBalance(userId);

        if (request.getAmount().compareTo(balance.getAvailableBalance()) > 0) {
            // Distinguish "you have not earned this" from "it has not cleared yet" —
            // an organiser looking at their sales figure needs to know which.
            if (balance.getHeldBalance() != null
                    && balance.getHeldBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BadRequestException(String.format(
                        "Only %s is available right now. %s is still held until %s — " +
                        "funds are released %d hours after an event ends.",
                        balance.getAvailableBalance(), balance.getHeldBalance(),
                        balance.getNextReleaseAt() != null ? balance.getNextReleaseAt() : "the event ends",
                        payoutHoldHours));
            }
            throw new BadRequestException("Insufficient balance. Available: " + balance.getAvailableBalance());
        }

        // Confirm the account with the bank before an irreversible transfer. Only
        // possible when a bank code was supplied; a provider outage returns null and
        // is allowed through rather than blocking a legitimate request.
        String verifiedName = null;
        if (request.getBankCode() != null && !request.getBankCode().isBlank()) {
            verifiedName = paystackService.resolveAccountName(
                    request.getAccountNumber(), request.getBankCode());

            if (verifiedName == null) {
                throw new BadRequestException(
                        "We could not verify that account number with the selected bank. " +
                        "Please check the details and try again.");
            }
        }

        Payout payout = Payout.builder()
                .user(user)
                .amount(request.getAmount())
                .bankName(request.getBankName())
                .bankCode(request.getBankCode())
                .accountNumber(request.getAccountNumber())
                // Prefer the name the bank holds over what was typed.
                .accountName(verifiedName != null ? verifiedName : request.getAccountName())
                .build();

        payout = payoutRepository.save(payout);

        log.info("Payout {} requested by user {} for {} to {} {}",
                payout.getReference(), userId, payout.getAmount(),
                payout.getBankName(), payout.getAccountNumber());

        return toResponse(payout);
    }

    public List<PayoutResponse> getPayoutHistory(Long userId) {
        return payoutRepository.findByUserIdOrderByRequestedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * What the organiser has, split by whether it has cleared its hold.
     *
     * <p>Previously this counted every paid order the moment it settled, so an
     * organiser could withdraw the full takings for an event that had not happened
     * yet — and then not run it. Only orders past their release time now count as
     * available.
     *
     * <p>Aggregated in the database rather than by loading every order: the old
     * version issued one query per event plus one per order.
     */
    public BalanceResponse getBalance(Long userId) {
        LocalDateTime now = LocalDateTime.now();

        BigDecimal released = orZero(orderRepository.sumReleasableByOrganizer(userId, now));
        BigDecimal held     = orZero(orderRepository.sumHeldByOrganizer(userId, now));
        BigDecimal earned   = orZero(orderRepository.sumEarnedByOrganizer(userId));

        // PROCESSING counts against the balance too: the money is in flight, and
        // treating it as available would let an organiser request it a second time.
        BigDecimal pending    = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.PENDING));
        BigDecimal processing = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.PROCESSING));
        BigDecimal paidOut    = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.PROCESSED));

        BigDecimal available = released
                .subtract(pending)
                .subtract(processing)
                .subtract(paidOut);

        return BalanceResponse.builder()
                .availableBalance(available.max(BigDecimal.ZERO))
                .heldBalance(held)
                .nextReleaseAt(orderRepository.findNextReleaseAt(userId, now))
                .pendingPayouts(pending.add(processing))
                .totalEarned(earned)
                .totalPaidOut(paidOut)
                .build();
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Marks a payout as paid, after finance has sent the money.
     *
     * <p>Records the fact of a transfer; it does not perform one. Phase 2 replaces
     * the manual step with the provider's Transfers API, at which point this
     * transitions PENDING to PROCESSING and a webhook completes it.
     *
     * <p>The claim is a conditional UPDATE rather than a read-then-write: two admins
     * on the same queue, or a double-clicked button, would otherwise both pass the
     * status check and mark the same payout twice.
     */
    @Transactional
    public PayoutResponse processPayout(Long payoutId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        int claimed = payoutRepository.claimStatus(
                payoutId, PayoutStatus.PENDING, PayoutStatus.PROCESSED,
                adminId, LocalDateTime.now());

        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (claimed == 0) {
            throw new BadRequestException(
                    "This payout is already " + payout.getStatus() + " and cannot be processed again.");
        }

        log.info("Payout {} marked PROCESSED by admin {} — {} to {} {}",
                payout.getReference(), adminId, payout.getAmount(),
                payout.getBankName(), payout.getAccountNumber());

        return toResponse(payout);
    }

    /**
     * Rejects a pending payout, returning the funds to the organiser's balance.
     *
     * <p>Nothing needs crediting back explicitly: the balance is derived, and a
     * FAILED payout simply stops being subtracted.
     */
    @Transactional
    public PayoutResponse rejectPayout(Long payoutId, Long adminId, String reason) {
        userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        int claimed = payoutRepository.claimStatus(
                payoutId, PayoutStatus.PENDING, PayoutStatus.FAILED,
                adminId, LocalDateTime.now());

        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (claimed == 0) {
            throw new BadRequestException(
                    "This payout is already " + payout.getStatus() + " and cannot be rejected.");
        }

        payout.setFailureReason(reason != null && !reason.isBlank()
                ? reason : "Rejected by admin");
        payout = payoutRepository.save(payout);

        log.info("Payout {} REJECTED by admin {}: {}",
                payout.getReference(), adminId, payout.getFailureReason());

        return toResponse(payout);
    }

    private PayoutResponse toResponse(Payout payout) {
        return PayoutResponse.builder()
                .id(payout.getId())
                .amount(payout.getAmount())
                .bankName(payout.getBankName())
                .accountNumber(payout.getAccountNumber())
                .accountName(payout.getAccountName())
                .status(payout.getStatus())
                .requestedAt(payout.getRequestedAt())
                .processedAt(payout.getProcessedAt())
                .build();
    }
}
