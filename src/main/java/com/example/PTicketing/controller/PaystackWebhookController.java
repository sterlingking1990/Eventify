package com.example.PTicketing.controller;

import com.example.PTicketing.service.OrderService;
import com.example.PTicketing.service.PaystackService;
import com.example.PTicketing.service.PayoutService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/paystack")
@RequiredArgsConstructor
public class PaystackWebhookController {

    private final PaystackService paystackService;
    private final OrderService orderService;
    private final PayoutService payoutService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("x-paystack-signature") String signature) {

        if (!paystackService.verifyWebhookSignature(payload, signature)) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            JsonNode body = objectMapper.readTree(payload);
            String event = body.get("event").asText();
            JsonNode data = body.get("data");
            String reference = data.get("reference").asText();

            if (event.startsWith("transfer.")) {
                // Field name for the failure reason varies by event/version — log the
                // raw payload once so it can be confirmed against a real test event.
                log.info("Transfer webhook {}: {}", event, data.toString());
                String failureReason = data.hasNonNull("reason") ? data.get("reason").asText()
                        : data.hasNonNull("message") ? data.get("message").asText()
                        : null;
                payoutService.handleTransferWebhook(event, reference, failureReason);
            } else {
                orderService.handlePaystackWebhook(event, reference);
            }

            return ResponseEntity.ok("Received");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing webhook");
        }
    }
}
