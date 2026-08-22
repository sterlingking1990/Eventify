package com.example.PTicketing.service;

import com.example.PTicketing.dto.paystack.PaystackTransferResult;
import com.example.PTicketing.dto.request.PayoutRequest;
import com.example.PTicketing.dto.response.BalanceResponse;
import com.example.PTicketing.dto.response.FeeInfoResponse;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final PlatformSettingsService platformSettingsService;
    private final PayoutNotificationService payoutNotificationService;

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

        // Frozen at request time, alongside the bank verification above: the
        // organiser needs to see the exact net amount before committing, and a
        // later admin change to the platform fee must never alter a request already
        // in flight.
        PlatformSettings settings = platformSettingsService.getOrCreate();
        BigDecimal feePercent = settings.getCashoutFeePercent();
        BigDecimal feeAmount = request.getAmount()
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = request.getAmount().subtract(feeAmount);

        Payout payout = Payout.builder()
                .user(user)
                .amount(request.getAmount())
                .bankName(request.getBankName())
                .bankCode(request.getBankCode())
                .accountNumber(request.getAccountNumber())
                // Prefer the name the bank holds over what was typed.
                .accountName(verifiedName != null ? verifiedName : request.getAccountName())
                .feePercentApplied(feePercent)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .slaHoursApplied(settings.getPayoutSlaHours())
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

        // PROCESSING and OTP_PENDING count against the balance too: the money is in
        // flight, and treating it as available would let an organiser request it a
        // second time.
        BigDecimal pending    = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.PENDING));
        BigDecimal processing = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.PROCESSING));
        BigDecimal otpPending = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.OTP_PENDING));
        BigDecimal paidOut    = orZero(payoutRepository.sumAmountByUserIdAndStatus(userId, PayoutStatus.PROCESSED));

        BigDecimal available = released
                .subtract(pending)
                .subtract(processing)
                .subtract(otpPending)
                .subtract(paidOut);

        return BalanceResponse.builder()
                .availableBalance(available.max(BigDecimal.ZERO))
                .heldBalance(held)
                .nextReleaseAt(orderRepository.findNextReleaseAt(userId, now))
                .pendingPayouts(pending.add(processing).add(otpPending))
                .totalEarned(earned)
                .totalPaidOut(paidOut)
                .build();
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Approves a pending payout and hands it to Paystack.
     *
     * <p>The claim is a conditional UPDATE rather than a read-then-write: two admins
     * on the same queue, or a double-clicked button, would otherwise both pass the
     * status check and act on the same payout twice. Any failure past that point
     * — creating the recipient, or the transfer call itself — moves the payout to
     * FAILED with a reason rather than leaving it stuck in PROCESSING, since funds
     * held there are not subtracted from the organiser's balance if nothing is
     * actually in flight at the provider.
     */
    @Transactional
    public PayoutResponse approvePayout(Long payoutId, Long adminId) {
        userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        int claimed = payoutRepository.claimStatus(
                payoutId, PayoutStatus.PENDING, PayoutStatus.PROCESSING,
                adminId, LocalDateTime.now());

        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (claimed == 0) {
            throw new BadRequestException(
                    "This payout is already " + payout.getStatus() + " and cannot be approved again.");
        }

        if (payout.getNetAmount() == null) {
            // Predates the cashout-fee feature — treat the full requested amount as net
            // rather than fail a request that was legitimately made before fees existed.
            payout.setNetAmount(payout.getAmount());
            payout.setFeeAmount(BigDecimal.ZERO);
            payout.setFeePercentApplied(BigDecimal.ZERO);
        }

        try {
            if (payout.getPaystackRecipientCode() == null) {
                if (payout.getBankCode() == null || payout.getBankCode().isBlank()) {
                    throw new BadRequestException(
                            "This request has no bank code on file and cannot be sent to Paystack.");
                }
                String recipientCode = paystackService.createTransferRecipient(
                        payout.getAccountName(), payout.getAccountNumber(), payout.getBankCode());
                payout.setPaystackRecipientCode(recipientCode);
            }

            PaystackTransferResult result = paystackService.initiateTransfer(
                    payout.getPaystackRecipientCode(), payout.getNetAmount(),
                    payout.getReference(), "Eventify payout " + payout.getReference());

            payout.setPaystackTransferCode(result.transferCode());
            applyTransferOutcome(payout, result);
            payout = payoutRepository.save(payout);

        } catch (BadRequestException e) {
            failPayout(payout, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Payout {} approve→transfer failed: {}", payout.getReference(), e.getMessage());
            failPayout(payout, "Could not initiate transfer: " + e.getMessage());
        }

        log.info("Payout {} approved by admin {} — now {}", payout.getReference(), adminId, payout.getStatus());
        return toResponse(payout);
    }

    /** Completes a transfer with the PIN Paystack sent to Brandible's registered contact. */
    @Transactional
    public PayoutResponse submitOtp(Long payoutId, String otp, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.OTP_PENDING) {
            throw new BadRequestException(
                    "This payout is " + payout.getStatus() + " and is not waiting on an OTP.");
        }

        PaystackTransferResult result = paystackService.finalizeTransfer(payout.getPaystackTransferCode(), otp);

        if ("invalid_otp".equals(result.status())) {
            throw new BadRequestException(
                    result.message() != null ? result.message() : "That OTP was rejected. Please try again.");
        }

        applyTransferOutcome(payout, result);
        payout = payoutRepository.save(payout);

        log.info("Payout {} OTP submitted by admin {} — now {}", payout.getReference(), adminId, payout.getStatus());
        return toResponse(payout);
    }

    /** Asks Paystack to resend the OTP, for when the original one expired unused. */
    @Transactional
    public PayoutResponse resendOtp(Long payoutId, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.OTP_PENDING) {
            throw new BadRequestException(
                    "This payout is " + payout.getStatus() + " and is not waiting on an OTP.");
        }

        boolean sent = paystackService.resendTransferOtp(payout.getPaystackTransferCode());
        if (!sent) {
            throw new BadRequestException("Could not resend the OTP. Please try again shortly.");
        }

        payout.setOtpRequestedAt(LocalDateTime.now());
        payout = payoutRepository.save(payout);
        return toResponse(payout);
    }

    /**
     * Reconciles a payout against a Paystack transfer webhook.
     *
     * <p>Tries both PROCESSING and OTP_PENDING as the "from" state: the webhook can
     * arrive before a manual OTP finalize (e.g. the admin closed the tab), and this
     * must not double-fire notifications if it also arrives after.
     */
    @Transactional
    public void handleTransferWebhook(String event, String reference, String failureReason) {
        Payout payout = payoutRepository.findByReference(reference).orElse(null);
        if (payout == null) {
            log.warn("Transfer webhook {} for unknown reference {}", event, reference);
            return;
        }

        PayoutStatus to = "transfer.success".equals(event) ? PayoutStatus.PROCESSED : PayoutStatus.FAILED;
        LocalDateTime now = LocalDateTime.now();

        int claimed = payoutRepository.claimStatusSystem(payout.getId(), PayoutStatus.PROCESSING, to, now);
        if (claimed == 0) {
            claimed = payoutRepository.claimStatusSystem(payout.getId(), PayoutStatus.OTP_PENDING, to, now);
        }
        if (claimed == 0) {
            log.info("Transfer webhook {} for {} ignored — already {}", event, reference, payout.getStatus());
            return;
        }

        payout = payoutRepository.findById(payout.getId()).orElseThrow();
        if (to == PayoutStatus.FAILED) {
            payout.setFailureReason(failureReason != null ? failureReason : "Transfer failed at provider");
            payout = payoutRepository.save(payout);
            payoutNotificationService.notifyDisbursementFailed(payout);
        } else {
            payoutNotificationService.notifyDisbursed(payout);
        }
    }

    private void applyTransferOutcome(Payout payout, PaystackTransferResult result) {
        switch (result.status()) {
            case "otp" -> payout.setStatus(PayoutStatus.OTP_PENDING);
            case "success" -> {
                payout.setStatus(PayoutStatus.PROCESSED);
                payout.setProcessedAt(LocalDateTime.now());
                payoutNotificationService.notifyDisbursed(payout);
            }
            case "pending" -> payout.setStatus(PayoutStatus.PROCESSING);
            default -> {
                failPayout(payout, result.message() != null ? result.message() : "Transfer failed at provider");
            }
        }
    }

    private void failPayout(Payout payout, String reason) {
        payout.setStatus(PayoutStatus.FAILED);
        payout.setFailureReason(reason);
        payout.setProcessedAt(LocalDateTime.now());
        payoutRepository.save(payout);
        payoutNotificationService.notifyDisbursementFailed(payout);
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

        payoutNotificationService.notifyRejected(payout);

        return toResponse(payout);
    }

    public FeeInfoResponse getFeeInfo() {
        return platformSettingsService.getFeeInfo();
    }

    /**
     * Flags cashout requests an admin hasn't acted on within their SLA.
     *
     * <p>Modeled on {@code IntegrationOrderService.releaseExpiredHolds()}: a sweep
     * rather than a per-request timer, since the volume here doesn't warrant one.
     * {@code slaBreachNotified} stops this from re-notifying the same request every
     * time the sweep runs.
     */
    @Scheduled(fixedRateString = "${integration.payout-sla-sweep-ms:1800000}")
    @Transactional
    public void flagOverduePayouts() {
        List<Payout> overdue = payoutRepository.findByStatusAndResponseDueAtBeforeAndSlaBreachNotifiedFalse(
                PayoutStatus.PENDING, LocalDateTime.now());

        if (overdue.isEmpty()) return;

        for (Payout p : overdue) {
            p.setSlaBreachNotified(true);
        }
        payoutRepository.saveAll(overdue);

        payoutNotificationService.notifySlaBreach(overdue);
        log.info("Flagged {} overdue payout(s)", overdue.size());
    }

    private PayoutResponse toResponse(Payout payout) {
        return PayoutResponse.builder()
                .id(payout.getId())
                .amount(payout.getAmount())
                .bankName(payout.getBankName())
                .accountNumber(payout.getAccountNumber())
                .accountName(payout.getAccountName())
                .status(payout.getStatus())
                .failureReason(payout.getFailureReason())
                .requestedAt(payout.getRequestedAt())
                .processedAt(payout.getProcessedAt())
                .feePercentApplied(payout.getFeePercentApplied())
                .feeAmount(payout.getFeeAmount())
                .netAmount(payout.getNetAmount())
                .responseDueAt(payout.getResponseDueAt())
                .build();
    }
}
