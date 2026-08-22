package com.example.PTicketing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row table of platform-wide, admin-editable settings.
 *
 * <p>Always read/written at id=1 — {@link com.example.PTicketing.service.PlatformSettingsService}
 * lazily inserts the default row the first time it's needed, so there is no seeder
 * and no migration required for the default to exist.
 */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    @Id
    private Long id;

    /** Percentage deducted from a cashout request's gross amount. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal cashoutFeePercent;

    /** Hours an admin has to approve or reject a cashout request before it's flagged overdue. */
    @Column(nullable = false)
    private Integer payoutSlaHours;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
