package com.example.PTicketing.dto.request;

import com.example.PTicketing.enums.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InitializeOrderRequest {

    @NotNull
    private Long eventId;

    @NotNull
    private Long ticketTypeId;

    @Positive
    private int quantity;

    @NotBlank
    @Email
    private String buyerEmail;

    @NotBlank
    private String buyerName;

    private String discountCode;

    private String referralCode;

    private PaymentMethod paymentMethod;
}
