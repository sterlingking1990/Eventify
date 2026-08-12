package com.example.PTicketing.entity;

import com.example.PTicketing.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * Idempotency key, and the reference the transfer will carry at the provider.
     *
     * <p>Unique so the same payout can never be issued twice: a retried request, a
     * double-clicked admin action or a replayed webhook all collide here rather
     * than sending money again.
     */
    @Column(unique = true, updatable = false)
    private String reference;

    private String bankName;

    /** Provider bank code, used to verify the account and to address the transfer. */
    private String bankCode;

    private String accountNumber;

    /**
     * The account name. Where a bank code was supplied this is the name resolved
     * from the bank, not what the organiser typed — a mismatch between the two is
     * the last chance to catch money heading to the wrong person.
     */
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    /** Why a FAILED payout failed, for the organiser and for support. */
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @PrePersist
    protected void onCreate() {
        this.requestedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = PayoutStatus.PENDING;
        }
        if (this.reference == null) {
            this.reference = "PAY-" + java.util.UUID.randomUUID()
                    .toString().replace("-", "").substring(0, 16).toUpperCase();
        }
    }
}
