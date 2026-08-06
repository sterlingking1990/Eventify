package com.example.PTicketing.controller;

import com.example.PTicketing.service.OrderService;
import com.example.PTicketing.service.PaystackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/paystack")
@RequiredArgsConstructor
public class PaystackWebhookController {

    private final PaystackService paystackService;
    private final OrderService orderService;
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
            String reference = body.get("data").get("reference").asText();

            orderService.handlePaystackWebhook(event, reference);

            return ResponseEntity.ok("Received");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing webhook");
        }
    }
}
