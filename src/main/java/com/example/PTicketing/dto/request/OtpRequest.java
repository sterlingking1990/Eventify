package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpRequest {
    @NotBlank
    private String otp;
}
