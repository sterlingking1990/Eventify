package com.example.PTicketing.service;

import com.example.PTicketing.dto.response.ExternalOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells the selling channel that tickets are ready, so it can deliver them the
 * way its buyer expects — a WhatsApp message rather than an email.
 *
 * <p>Runs outside the confirming transaction and never throws. By the time this
 * is called the buyer has paid and the tickets exist; a delivery hiccup must not
 * undo either. Failures are logged loudly and left for reconciliation, because
 * the channel can always re-fetch the order by reference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketDeliveryService {

    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;

    @Value("${integration.delivery.webhook-url:}")
    private String deliveryWebhookUrl;

    @Value("${integration.delivery.api-key:}")
    private String deliveryApiKey;

    @Async
    public void deliverAsync(ExternalOrderResponse order, String buyerPhone, String sourceChannel) {
        if (deliveryWebhookUrl == null || deliveryWebhookUrl.isBlank()) {
            log.warn("No integration.delivery.webhook-url configured — order {} has tickets " +
                     "but nothing will be sent to {}", order.getOrderRef(), sourceChannel);
            return;
        }
        if (buyerPhone == null || buyerPhone.isBlank()) {
            log.warn("Order {} has no buyer phone — skipping channel delivery", order.getOrderRef());
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_ref", order.getOrderRef());
        payload.put("paystack_reference", order.getPaystackReference());
        payload.put("source_channel", sourceChannel);
        payload.put("buyer_phone", buyerPhone);
        payload.put("event_title", order.getEventTitle());
        payload.put("event_slug", order.getEventSlug());
        payload.put("event_venue", order.getEventVenue());
        payload.put("event_start_date", String.valueOf(order.getEventStartDate()));
        payload.put("ticket_type_name", order.getTicketTypeName());
        payload.put("quantity", order.getQuantity());
        payload.put("total_amount", order.getTotalAmount());

        List<Map<String, String>> tickets = order.getTickets() == null ? List.of()
                : order.getTickets().stream()
                    .map(t -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("qr_code", t.getQrCode());
                        m.put("qr_image_url", t.getQrImageUrl());
                        return m;
                    })
                    .toList();
        payload.put("tickets", tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (deliveryApiKey != null && !deliveryApiKey.isBlank()) {
            headers.add("X-Service-Key", deliveryApiKey);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restTemplate.postForEntity(deliveryWebhookUrl, entity, String.class);
                log.info("Delivered {} ticket(s) for order {} to {}",
                        tickets.size(), order.getOrderRef(), sourceChannel);
                return;
            } catch (Exception e) {
                log.warn("Ticket delivery attempt {}/{} failed for order {}: {}",
                        attempt, MAX_ATTEMPTS, order.getOrderRef(), e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    // Loud on purpose: the buyer has paid and holds valid tickets they
                    // have not been sent. Needs a human or a replay job.
                    log.error("DELIVERY FAILED for paid order {} (phone {}) after {} attempts — " +
                              "tickets are valid and retrievable by reference {}",
                            order.getOrderRef(), buyerPhone, MAX_ATTEMPTS, order.getPaystackReference());
                    return;
                }
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
