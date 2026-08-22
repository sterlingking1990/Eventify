package com.example.PTicketing.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class PaystackConfig {

    @Value("${paystack.secret-key}")
    private String secretKey;

    @Value("${paystack.public-key}")
    private String publicKey;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public String getInitializeUrl() {
        return "https://api.paystack.co/transaction/initialize";
    }

    public String getVerifyUrl() {
        return "https://api.paystack.co/transaction/verify/";
    }

    public String getPaymentCallbackUrl() {
        return frontendUrl + "/payment/success";
    }

    public String getTransferRecipientUrl() {
        return "https://api.paystack.co/transferrecipient";
    }

    public String getTransferUrl() {
        return "https://api.paystack.co/transfer";
    }

    public String getFinalizeTransferUrl() {
        return "https://api.paystack.co/transfer/finalize_transfer";
    }

    public String getResendOtpUrl() {
        return "https://api.paystack.co/transfer/resend_otp";
    }
}
