package com.example.PTicketing.repository;

import com.example.PTicketing.entity.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsletterRepository extends JpaRepository<NewsletterSubscriber, Long> {
    Optional<NewsletterSubscriber> findByEmail(String email);
    boolean existsByEmail(String email);
    List<NewsletterSubscriber> findByIsActiveTrue();
}
