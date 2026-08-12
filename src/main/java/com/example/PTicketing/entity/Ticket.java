package com.example.PTicketing.entity;

import com.example.PTicketing.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    private String buyerEmail;

    private String buyerName;

    @Column(nullable = false, unique = true)
    private String qrCode;

    // Hosted PNG of the QR, needed by channels that deliver a message rather than
    // an email. Persisted so a retried confirmation reuses the existing image
    // instead of paying to upload the same code again.
    @Column(columnDefinition = "TEXT")
    private String qrImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(updatable = false)
    private LocalDateTime purchasedAt;

    private LocalDateTime checkedInAt;

    @PrePersist
    protected void onCreate() {
        this.purchasedAt = LocalDateTime.now();
    }
}
