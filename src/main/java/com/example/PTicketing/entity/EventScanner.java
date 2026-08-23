package com.example.PTicketing.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Grants one SCANNER account the right to check tickets in for one event.
 *
 * <p>Exists because the scan endpoint used to trust any SCANNER role user with
 * every event's gate. Role alone says what an account is; this table says where
 * it works. The organizer owns the assignments and the scan path enforces them,
 * so a leaked scanner login only opens the events it was given.
 */
@Entity
@Table(name = "event_scanners",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventScanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // The assigned account. Expected to carry the SCANNER role; enforced at
    // assignment time in EventScannerService rather than here, because a role
    // change after the fact is caught again at scan time by the same service.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
