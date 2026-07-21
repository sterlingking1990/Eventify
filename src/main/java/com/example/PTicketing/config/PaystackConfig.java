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

    public String getInitializeUrl() {
        return "https://api.paystack.co/transaction/initialize";
    }

    public String getVerifyUrl() {
        return "https://api.paystack.co/transaction/verify/";
    }
}
