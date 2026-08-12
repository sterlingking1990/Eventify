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

    /**
     * Provider bank code (from GET /api/v1/payouts/banks).
     *
     * <p>Optional for now so existing clients keep working, but supplying it is what
     * enables account-name verification before an irreversible transfer. Should
     * become required once the frontend sends it.
     */
    private String bankCode;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountName;
}
