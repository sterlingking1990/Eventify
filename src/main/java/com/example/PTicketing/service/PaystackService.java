package com.example.PTicketing.service;

import com.example.PTicketing.config.PaystackConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaystackService {

    private final PaystackConfig paystackConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String initializeTransaction(String email, BigDecimal amount, String reference) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", email,
                "amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString(),
                "reference", reference
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    paystackConfig.getInitializeUrl(),
                    request,
                    JsonNode.class
            );

            if (response.getBody() != null && response.getBody().has("data")) {
                return response.getBody().get("data").get("authorization_url").asText();
            }
            throw new RuntimeException("Paystack initialization failed");
        } catch (Exception e) {
            throw new RuntimeException("Paystack error: " + e.getMessage());
        }
    }

    public boolean verifyTransaction(String reference) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    paystackConfig.getVerifyUrl() + reference,
                    HttpMethod.GET,
                    request,
                    JsonNode.class
            );

            if (response.getBody() != null && response.getBody().has("data")) {
                String status = response.getBody().get("data").get("status").asText();
                return "success".equalsIgnoreCase(status);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret = new SecretKeySpec(
                    paystackConfig.getSecretKey().getBytes(), "HmacSHA512"
            );
            mac.init(secret);
            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString().equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
