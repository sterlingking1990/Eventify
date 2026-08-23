package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class PayoutResponse {
    private Long id;
    private BigDecimal amount;
    private String bankName;
    private String accountNumber;
    private String accountName;
    private PayoutStatus status;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private BigDecimal feePercentApplied;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
    private LocalDateTime responseDueAt;
}
