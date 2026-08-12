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

    public String initializeTransaction(String email, BigDecimal amount, String reference, String callbackUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        body.put("reference", reference);
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            body.put("callback_url", callbackUrl);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paystackConfig.getInitializeUrl(),
                    request,
                    String.class
            );

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            if (responseBody.has("data") && responseBody.get("data").has("authorization_url")) {
                return responseBody.get("data").get("authorization_url").asText();
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
            ResponseEntity<String> response = restTemplate.exchange(
                    paystackConfig.getVerifyUrl() + reference,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            if (responseBody.has("data") && responseBody.get("data").has("status")) {
                return "success".equalsIgnoreCase(responseBody.get("data").get("status").asText());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves an account number against a bank, returning the registered name.
     *
     * <p>A payout is irreversible once sent, and the organiser types their own
     * account details. Confirming the name the bank holds is the last opportunity
     * to catch a transposed digit before the money is gone.
     *
     * @return the account name, or null if it could not be resolved
     */
    public String resolveAccountName(String accountNumber, String bankCode) {
        if (accountNumber == null || bankCode == null) return null;

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.paystack.co/bank/resolve?account_number=" + accountNumber
                            + "&bank_code=" + bankCode,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            JsonNode body = objectMapper.readTree(response.getBody());
            if (body.path("status").asBoolean(false)) {
                return body.path("data").path("account_name").asText(null);
            }
            return null;
        } catch (Exception e) {
            // Treated as "could not verify" rather than "invalid" — a provider
            // outage should not block a legitimate payout request.
            return null;
        }
    }

    /** The provider's bank list, so the organiser picks a code rather than typing a name. */
    public JsonNode listBanks() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.paystack.co/bank?country=nigeria&perPage=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return objectMapper.readTree(response.getBody()).path("data");
        } catch (Exception e) {
            throw new RuntimeException("Could not load bank list: " + e.getMessage());
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
