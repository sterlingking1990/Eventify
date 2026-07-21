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
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
}
