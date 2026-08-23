package com.example.PTicketing.service;

import com.example.PTicketing.enums.EventType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The buyer-side markup, in one place — currently none.
 *
 * <p>Buyers pay exactly face value. The platform's cut comes from organisers at
 * cashout instead ({@code platform_settings.cashout_fee_percent}), so adding a
 * fee here would double-charge the same ticket money twice on the way through
 * the business.
 *
 * <p>Both {@code OrderService} and {@code IntegrationOrderService} price through
 * this method, so if a booking fee ever returns it cannot drift between the web
 * checkout and external channels — change it here or nowhere.
 *
 * <p>The eventType/subtotal/quantity parameters are kept: callers already hold
 * them, and a reintroduced per-event or per-ticket fee will need exactly these.
 */
@Service
public class PricingService {

    public BigDecimal calculateFee(EventType eventType, BigDecimal subtotal, int quantity) {
        return BigDecimal.ZERO;
    }
}
