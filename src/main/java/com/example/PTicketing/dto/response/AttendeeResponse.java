package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per issued ticket on an organiser's attendee list.
 *
 * <p>Ticket-level rather than order-level on purpose: the door checks tickets, and
 * a buyer purchasing four is four people arriving. Orders are still identifiable
 * through {@code orderRef}, which repeats across the rows of a single purchase.
 *
 * <p>Carries buyer contact details, so every route to it must be scoped to the
 * event's own organiser.
 */
@Data
@Builder
@AllArgsConstructor
public class AttendeeResponse {

    private Long ticketId;
    private String ticketTypeName;
    private String buyerName;
    private String buyerEmail;
    /** Only present for orders placed through a message channel. */
    private String buyerPhone;

    private String orderRef;
    /** "web", "brandible-whatsapp", or null for older orders. */
    private String sourceChannel;
    private BigDecimal amountPaid;
    private LocalDateTime purchasedAt;

    private TicketStatus status;
    private boolean checkedIn;
    private LocalDateTime checkedInAt;

    /**
     * The gate code. Included so an organiser can resolve "I paid but lost my
     * ticket" without a support round trip — but it is the credential that admits
     * someone, so this whole payload stays organiser-only.
     */
    private String qrCode;
}
