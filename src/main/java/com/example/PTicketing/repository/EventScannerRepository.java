package com.example.PTicketing.repository;

import com.example.PTicketing.entity.EventScanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventScannerRepository extends JpaRepository<EventScanner, Long> {
    boolean existsByEventIdAndUserId(Long eventId, Long userId);
    List<EventScanner> findByEventId(Long eventId);
    Optional<EventScanner> findByEventIdAndUserId(Long eventId, Long userId);
}
