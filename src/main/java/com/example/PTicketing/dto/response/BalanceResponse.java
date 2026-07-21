package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class BalanceResponse {
    private BigDecimal availableBalance;
    private BigDecimal pendingPayouts;
    private BigDecimal totalEarned;
}
