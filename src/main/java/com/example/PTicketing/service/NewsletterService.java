package com.example.PTicketing.service;

import com.example.PTicketing.entity.NewsletterSubscriber;
import com.example.PTicketing.exception.DuplicateResourceException;
import com.example.PTicketing.repository.NewsletterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NewsletterService {

    private final NewsletterRepository newsletterRepository;

    @Transactional
    public void subscribe(String email) {
        if (newsletterRepository.existsByEmail(email)) {
            NewsletterSubscriber existing = newsletterRepository.findByEmail(email).orElse(null);
            if (existing != null && !existing.isActive()) {
                existing.setActive(true);
                existing.setUnsubscribedAt(null);
                newsletterRepository.save(existing);
                return;
            }
            throw new DuplicateResourceException("Email already subscribed");
        }

        NewsletterSubscriber subscriber = NewsletterSubscriber.builder()
                .email(email)
                .build();

        newsletterRepository.save(subscriber);
    }

    @Transactional
    public void unsubscribe(String email) {
        NewsletterSubscriber subscriber = newsletterRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        subscriber.setActive(false);
        subscriber.setUnsubscribedAt(LocalDateTime.now());
        newsletterRepository.save(subscriber);
    }
}
