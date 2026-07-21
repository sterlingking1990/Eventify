package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PayoutRequest {

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String bankName;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountName;
}
