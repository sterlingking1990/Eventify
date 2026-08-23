package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class PlatformSettingsResponse {
    private BigDecimal cashoutFeePercent;
    private Integer payoutSlaHours;
    private LocalDateTime updatedAt;
    private String updatedByName;
}
