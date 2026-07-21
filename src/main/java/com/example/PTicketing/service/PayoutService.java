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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public PayoutResponse requestPayout(PayoutRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BalanceResponse balance = getBalance(userId);
        if (request.getAmount().compareTo(balance.getAvailableBalance()) > 0) {
            throw new BadRequestException("Insufficient balance. Available: " + balance.getAvailableBalance());
        }

        Payout payout = Payout.builder()
                .user(user)
                .amount(request.getAmount())
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .build();

        payout = payoutRepository.save(payout);
        return toResponse(payout);
    }

    public List<PayoutResponse> getPayoutHistory(Long userId) {
        return payoutRepository.findByUserIdOrderByRequestedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BalanceResponse getBalance(Long userId) {
        List<Event> userEvents = eventRepository.findByOrganizerId(userId);

        BigDecimal totalEarned = BigDecimal.ZERO;
        for (Event event : userEvents) {
            List<Order> paidOrders = orderRepository.findByEventId(event.getId())
                    .stream()
                    .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                    .toList();
            for (Order o : paidOrders) {
                totalEarned = totalEarned.add(
                        o.getTotalAmount().subtract(o.getFeeAmount() != null ? o.getFeeAmount() : BigDecimal.ZERO)
                );
            }
        }

        BigDecimal pendingPayouts = payoutRepository
                .sumAmountByUserIdAndStatus(userId, PayoutStatus.PENDING);
        if (pendingPayouts == null) pendingPayouts = BigDecimal.ZERO;

        BigDecimal processedPayouts = payoutRepository
                .sumAmountByUserIdAndStatus(userId, PayoutStatus.PROCESSED);
        if (processedPayouts == null) processedPayouts = BigDecimal.ZERO;

        BigDecimal availableBalance = totalEarned.subtract(pendingPayouts).subtract(processedPayouts);

        return BalanceResponse.builder()
                .availableBalance(availableBalance.max(BigDecimal.ZERO))
                .pendingPayouts(pendingPayouts)
                .totalEarned(totalEarned)
                .build();
    }

    @Transactional
    public PayoutResponse processPayout(Long payoutId, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new BadRequestException("Payout is not in pending status");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        payout.setStatus(PayoutStatus.PROCESSED);
        payout.setProcessedAt(LocalDateTime.now());
        payout.setProcessedBy(admin);

        payout = payoutRepository.save(payout);
        return toResponse(payout);
    }

    @Transactional
    public PayoutResponse rejectPayout(Long payoutId, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new BadRequestException("Payout is not in pending status");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        payout.setStatus(PayoutStatus.FAILED);
        payout.setProcessedAt(LocalDateTime.now());
        payout.setProcessedBy(admin);

        payout = payoutRepository.save(payout);
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
