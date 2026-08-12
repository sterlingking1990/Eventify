package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What an external sales channel needs back: enough to quote a price, and after
 * confirmation, enough to deliver the tickets over its own channel.
 */
@Data
@Builder
@AllArgsConstructor
public class ExternalOrderResponse {

    private String orderRef;
    private String paystackReference;
    private PaymentStatus paymentStatus;

    private Long eventId;
    private String eventTitle;
    private String eventSlug;
    private String eventVenue;
    private LocalDateTime eventStartDate;
    private LocalDateTime eventEndDate;
    private Long organizerId;

    private String ticketTypeName;
    private int quantity;

    private BigDecimal subtotal;
    private BigDecimal feeAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currency;

    /**
     * When the organiser's share of this order becomes withdrawable —
     * event end plus the configured hold. Null until the event end is known.
     */
    private LocalDateTime releasableAt;

    /** Populated only once payment is confirmed. */
    private List<IssuedTicket> tickets;

    /**
     * True when this confirmation actually transitioned the order and minted
     * tickets; false when the order was already paid and these tickets were
     * returned from a previous run. Lets the caller avoid re-sending a message.
     */
    private boolean newlyIssued;

    @Data
    @Builder
    @AllArgsConstructor
    public static class IssuedTicket {
        private Long ticketId;
        /** The opaque value encoded in the QR image; what the scanner reads. */
        private String qrCode;
        /** Hosted PNG of the QR, ready to attach to a message. Null if upload failed. */
        private String qrImageUrl;
        private String ticketTypeName;
    }
}
