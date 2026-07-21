package com.example.PTicketing.dto.request;

import com.example.PTicketing.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateDiscountRequest {

    @NotBlank
    private String code;

    @NotNull
    private DiscountType type;

    @NotNull
    @Positive
    private BigDecimal value;

    private int maxUsage;

    private BigDecimal minPurchaseAmount;

    private LocalDateTime expiresAt;
}
