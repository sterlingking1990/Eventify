package com.example.PTicketing.entity;

import com.example.PTicketing.enums.PaymentMethod;
import com.example.PTicketing.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, unique = true)
    private String orderRef;

    private BigDecimal subtotal;

    private BigDecimal feeAmount;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    private String currency;

    private String paystackReference;

    private String discountCode;

    private BigDecimal discountAmount;

    private String referralCode;

    private String buyerEmail;

    private String buyerName;

    // Set when the order originates from a WhatsApp channel rather than the web
    // checkout, where a phone number is the only identity we have.
    private String buyerPhone;

    // Identifies the external channel that created this order (e.g. "brandible-whatsapp").
    // Null for orders placed through the Eventify web app.
    private String sourceChannel;

    // External-channel orders reserve their tickets up front so a bank transfer
    // that takes minutes to arrive cannot be beaten to the last seat. This is when
    // that reservation lapses and the stock goes back; null means no hold is held.
    private LocalDateTime holdExpiresAt;

    // When the organiser's share of this order becomes withdrawable: the event's
    // end plus a hold period. Stamped at sale rather than derived at read time, so
    // an organiser cannot bring funds forward by editing the event date after the
    // fact — see EventService, which only ever pushes this later.
    //
    // Null means held. Orders predating this column have no value and stay locked
    // until backfilled; that is the safe direction to fail.
    private LocalDateTime releasableAt;

    private Long ticketTypeId;

    private Integer quantity;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
