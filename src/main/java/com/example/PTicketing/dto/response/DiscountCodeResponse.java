package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class DiscountCodeResponse {
    private Long id;
    private String code;
    private DiscountType type;
    private BigDecimal value;
    private int maxUsage;
    private int usedCount;
    private BigDecimal minPurchaseAmount;
    private LocalDateTime expiresAt;
    private boolean isActive;
}
