package com.example.PTicketing.service;

import com.example.PTicketing.enums.EventType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The platform's markup, in one place.
 *
 * <p>Extracted so the web checkout and external sales channels cannot drift apart
 * on what a ticket costs. Both {@code OrderService} and
 * {@code IntegrationOrderService} price through here.
 */
@Service
public class PricingService {

    /**
     * @param subtotal amount after any discount, before fees
     * @param quantity ticket count, used by the per-ticket component
     */
    public BigDecimal calculateFee(EventType eventType, BigDecimal subtotal, int quantity) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return switch (eventType) {
            case VOTING -> subtotal.multiply(BigDecimal.valueOf(0.08))
                    .setScale(2, RoundingMode.HALF_UP);
            case NORMAL -> subtotal.multiply(BigDecimal.valueOf(0.05))
                    .add(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(quantity)))
                    .setScale(2, RoundingMode.HALF_UP);
        };
    }
}
