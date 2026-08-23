package com.example.PTicketing.service;

import com.example.PTicketing.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Replays channel delivery for paid orders whose message may never have landed.
 *
 * <p>{@link TicketDeliveryService} gives up after three attempts and leaves only
 * log lines behind — a buyer who paid, holding valid tickets that no WhatsApp
 * message ever carried. Rather than track per-order delivery state (a schema
 * change plus bookkeeping on every path), this sweep simply re-fires every
 * candidate inside its window.
 *
 * <p>That is safe because the far end is idempotent: send-ticket-whatsapp claims
 * the session before messaging and answers already_delivered on repeats. The
 * worst case is a redundant webhook call per interval, never a duplicate
 * message to the buyer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryReconciliationService {

    private final OrderRepository orderRepository;
    private final IntegrationOrderService integrationOrderService;

    @Value("${integration.delivery.webhook-url:}")
    private String deliveryWebhookUrl;

    /** Younger than this, the original attempt plus its retries may still be in flight. */
    @Value("${integration.delivery.reconcile-min-age-minutes:10}")
    private int minAgeMinutes;

    /** Older than this, an event may have passed; a human decides rather than a timer. */
    @Value("${integration.delivery.reconcile-max-hours:48}")
    private int maxHours;

    @Scheduled(fixedRateString = "${integration.delivery.reconcile-ms:900000}")
    public void redeliverUnconfirmedOrders() {
        if (deliveryWebhookUrl == null || deliveryWebhookUrl.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        var candidates = orderRepository.findChannelDeliveryCandidates(
                now.minusHours(maxHours),
                now.minusMinutes(minAgeMinutes));

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Delivery reconciliation: replaying {} channel order(s) "
                        + "(paid between {} and {})",
                candidates.size(), now.minusHours(maxHours), now.minusMinutes(minAgeMinutes));

        for (var order : candidates) {
            // deliverExistingOrder never throws — one bad reference must not stop
            // the sweep from reaching the orders behind it.
            integrationOrderService.deliverExistingOrder(order.getPaystackReference());
        }

        log.info("Delivery reconciliation: replay complete");
    }
}
