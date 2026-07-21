package com.example.PTicketing.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "newsletter_subscribers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private boolean isActive;

    @Column(updatable = false)
    private LocalDateTime subscribedAt;

    private LocalDateTime unsubscribedAt;

    @PrePersist
    protected void onCreate() {
        this.subscribedAt = LocalDateTime.now();
        this.isActive = true;
    }
}
