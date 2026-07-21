package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.CreateDiscountRequest;
import com.example.PTicketing.dto.response.DiscountCodeResponse;
import com.example.PTicketing.entity.DiscountCode;
import com.example.PTicketing.entity.Event;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.DuplicateResourceException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.DiscountCodeRepository;
import com.example.PTicketing.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository discountRepository;
    private final EventRepository eventRepository;

    @Transactional
    public DiscountCodeResponse createDiscount(Long eventId, CreateDiscountRequest request, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to manage discounts for this event");
        }

        if (discountRepository.findByCode(request.getCode()).isPresent()) {
            throw new DuplicateResourceException("Discount code already exists");
        }

        DiscountCode dc = DiscountCode.builder()
                .event(event)
                .code(request.getCode().toUpperCase())
                .type(request.getType())
                .value(request.getValue())
                .maxUsage(request.getMaxUsage())
                .minPurchaseAmount(request.getMinPurchaseAmount())
                .expiresAt(request.getExpiresAt())
                .build();

        dc = discountRepository.save(dc);
        return toResponse(dc);
    }

    public List<DiscountCodeResponse> getDiscounts(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to view discounts for this event");
        }

        return discountRepository.findByEventId(eventId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteDiscount(Long discountId, Long userId) {
        DiscountCode dc = discountRepository.findById(discountId)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found"));

        if (!dc.getEvent().getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to delete this discount");
        }

        discountRepository.delete(dc);
    }

    @Transactional
    public void validateAndApply(String code, Long eventId) {
        DiscountCode dc = discountRepository.findByCodeAndEventId(code, eventId)
                .orElseThrow(() -> new BadRequestException("Invalid discount code for this event"));

        if (!dc.isActive()) {
            throw new BadRequestException("Discount code is no longer active");
        }

        if (dc.getExpiresAt() != null && LocalDateTime.now().isAfter(dc.getExpiresAt())) {
            throw new BadRequestException("Discount code has expired");
        }

        if (dc.getMaxUsage() > 0 && dc.getUsedCount() >= dc.getMaxUsage()) {
            throw new BadRequestException("Discount code usage limit reached");
        }
    }

    public BigDecimal calculateDiscount(String code, Long eventId, BigDecimal amount) {
        DiscountCode dc = discountRepository.findByCodeAndEventId(code, eventId)
                .orElseThrow(() -> new BadRequestException("Invalid discount code"));

        return switch (dc.getType()) {
            case PERCENTAGE -> amount.multiply(dc.getValue().divide(BigDecimal.valueOf(100)));
            case FIXED -> dc.getValue().min(amount);
        };
    }

    @Transactional
    public DiscountCode incrementUsage(String code) {
        DiscountCode dc = discountRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Invalid discount code"));
        dc.setUsedCount(dc.getUsedCount() + 1);
        return discountRepository.save(dc);
    }

    private DiscountCodeResponse toResponse(DiscountCode dc) {
        return DiscountCodeResponse.builder()
                .id(dc.getId())
                .code(dc.getCode())
                .type(dc.getType())
                .value(dc.getValue())
                .maxUsage(dc.getMaxUsage())
                .usedCount(dc.getUsedCount())
                .minPurchaseAmount(dc.getMinPurchaseAmount())
                .expiresAt(dc.getExpiresAt())
                .isActive(dc.isActive())
                .build();
    }
}
