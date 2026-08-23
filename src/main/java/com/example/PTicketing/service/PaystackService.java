package com.example.PTicketing.service;

import com.example.PTicketing.config.PaystackConfig;
import com.example.PTicketing.dto.paystack.PaystackTransferResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.util.List;
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

    /**
     * The provider's bank list, so the organiser picks a code rather than typing a name.
     *
     * <p>Returns plain maps rather than a Jackson {@code JsonNode} on purpose: this
     * app's HTTP layer serialises with Jackson 3 (tools.jackson, via Boot 4), while
     * the tree above is Jackson 2 (com.fasterxml, via jjwt). A foreign node type is
     * not recognised as JSON and gets bean-serialised into its getter metadata —
     * {"array":true,"bigDecimal":false,...} — which is exactly what shipped to the
     * organiser dashboard and emptied the bank dropdown.
     */
    public List<Map<String, Object>> listBanks() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.paystack.co/bank?country=nigeria&perPage=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (!data.isArray()) {
                throw new IllegalStateException(
                        "Unexpected bank list response from provider: " + response.getBody());
            }
            return objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Could not load bank list: " + e.getMessage());
        }
    }

    /**
     * Registers a bank account with Paystack so it can be addressed by a transfer.
     *
     * @return the recipient code, to be cached on the payout and reused
     */
    public String createTransferRecipient(String accountName, String accountNumber, String bankCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("type", "nuban");
        body.put("name", accountName);
        body.put("account_number", accountNumber);
        body.put("bank_code", bankCode);
        body.put("currency", "NGN");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paystackConfig.getTransferRecipientUrl(), request, String.class);
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            if (responseBody.path("status").asBoolean(false)) {
                return responseBody.path("data").path("recipient_code").asText(null);
            }
            throw new RuntimeException(responseBody.path("message").asText("recipient creation failed"));
        } catch (Exception e) {
            throw new RuntimeException("Could not create transfer recipient: " + e.getMessage());
        }
    }

    /**
     * Hands a payout to Paystack. The response status decides what happens next:
     * "otp" means Paystack has sent a PIN to Brandible's registered contact and
     * {@link #finalizeTransfer} must be called with it before money moves.
     */
    public PaystackTransferResult initiateTransfer(String recipientCode, BigDecimal netAmount,
                                                    String reference, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("source", "balance");
        body.put("amount", netAmount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        body.put("recipient", recipientCode);
        body.put("reason", reason);
        body.put("reference", reference);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paystackConfig.getTransferUrl(), request, String.class);
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            JsonNode data = responseBody.path("data");
            return new PaystackTransferResult(
                    data.path("status").asText("failed"),
                    data.path("transfer_code").asText(null),
                    data.path("reference").asText(reference),
                    responseBody.path("message").asText(null));
        } catch (Exception e) {
            return new PaystackTransferResult("failed", null, reference, e.getMessage());
        }
    }

    /**
     * Completes a transfer with the PIN Paystack sent out-of-band.
     *
     * <p>An invalid or expired OTP is not a transfer failure — it means whoever
     * holds the PIN mistyped or waited too long. That is reported as
     * {@code "invalid_otp"} rather than {@code "failed"} so the caller leaves the
     * payout in OTP_PENDING for a retry instead of failing it outright.
     */
    public PaystackTransferResult finalizeTransfer(String transferCode, String otp) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("transfer_code", transferCode);
        body.put("otp", otp);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paystackConfig.getFinalizeTransferUrl(), request, String.class);
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            JsonNode data = responseBody.path("data");
            return new PaystackTransferResult(
                    data.path("status").asText("success"),
                    transferCode,
                    data.path("reference").asText(null),
                    responseBody.path("message").asText(null));
        } catch (HttpClientErrorException e) {
            String message = extractMessage(e.getResponseBodyAsString());
            boolean invalidOtp = message != null && message.toLowerCase().contains("otp");
            return new PaystackTransferResult(invalidOtp ? "invalid_otp" : "failed", transferCode, null, message);
        } catch (Exception e) {
            return new PaystackTransferResult("failed", transferCode, null, e.getMessage());
        }
    }

    /** Asks Paystack to re-send the OTP for a transfer whose PIN expired before anyone entered it. */
    public boolean resendTransferOtp(String transferCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + paystackConfig.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("transfer_code", transferCode);
        body.put("reason", "resend_otp");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paystackConfig.getResendOtpUrl(), request, String.class);
            return objectMapper.readTree(response.getBody()).path("status").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractMessage(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).path("message").asText(null);
        } catch (Exception e) {
            return null;
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
