package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Non-sensitive subset of {@link PlatformSettingsResponse} any authenticated user may read. */
@Data
@Builder
@AllArgsConstructor
public class FeeInfoResponse {
    private BigDecimal cashoutFeePercent;
    private Integer payoutSlaHours;
}
