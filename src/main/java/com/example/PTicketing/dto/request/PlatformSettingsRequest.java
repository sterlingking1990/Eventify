package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlatformSettingsRequest {

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @DecimalMax(value = "100", inclusive = true)
    private BigDecimal cashoutFeePercent;

    @NotNull
    @Min(1)
    private Integer payoutSlaHours;
}
