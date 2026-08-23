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

    /**
     * Set when the bank-verified account holder's name shares no token with the
     * organiser's signup name — or when no verification happened at all (request
     * without a bank code). A flag for the approving admin, not a rejection: real
     * accounts differ from signup names in ordinary ways, so this narrows who
     * deserves a second look rather than deciding the money's fate.
     */
    @Column
    private Boolean accountNameMismatch;

    /** Why a FAILED payout failed, for the organiser and for support. */
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    /** Cashout fee percent in effect when this request was made — frozen so a later admin change never changes it. */
    private BigDecimal feePercentApplied;

    /** amount * feePercentApplied / 100, at request time. */
    private BigDecimal feeAmount;

    /** amount - feeAmount — what actually gets sent to Paystack. */
    private BigDecimal netAmount;

    /** Admin-response SLA in effect when this request was made, in hours. */
    private Integer slaHoursApplied;

    /** requestedAt + slaHoursApplied — when this request is considered overdue for an admin response. */
    private LocalDateTime responseDueAt;

    /** Whether the SLA sweep has already notified admins this request is overdue, so it isn't repeated every sweep. */
    @Builder.Default
    private boolean slaBreachNotified = false;

    /** Paystack transfer-recipient code, cached so it's created at most once per payout. */
    private String paystackRecipientCode;

    /** Paystack transfer code, needed to finalize or resend an OTP for this transfer. */
    private String paystackTransferCode;

    /** When an OTP was last requested from Paystack for this transfer, to drive a "resend" cooldown in the UI. */
    private LocalDateTime otpRequestedAt;

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
        if (this.slaHoursApplied != null) {
            this.responseDueAt = this.requestedAt.plusHours(this.slaHoursApplied);
        }
    }
}
