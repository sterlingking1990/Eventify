package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Creates a PENDING order on behalf of an external sales channel.
 *
 * <p>Differs from {@link InitializeOrderRequest} in two ways: the caller supplies
 * the Paystack reference (because the charge is created on the channel's own
 * Paystack account, not ours), and the buyer is identified by phone rather than
 * by a logged-in user.
 */
@Data
public class CreateExternalOrderRequest {

    @NotNull
    private Long eventId;

    @NotNull
    private Long ticketTypeId;

    @Positive
    private int quantity;

    /**
     * The reference the calling channel will use when charging Paystack. It is
     * the single join key across all three systems, so confirmation can find
     * this order without any shared state beyond the string itself.
     */
    @NotBlank
    @Size(max = 100)
    private String paystackReference;

    @NotBlank
    @Size(max = 20)
    private String buyerPhone;

    private String buyerName;

    /** Optional — a placeholder is derived from the phone when absent. */
    @Email
    private String buyerEmail;

    private String discountCode;

    private String referralCode;

    /** Free-text channel identifier, e.g. "brandible-whatsapp". */
    @Size(max = 50)
    private String sourceChannel;
}
