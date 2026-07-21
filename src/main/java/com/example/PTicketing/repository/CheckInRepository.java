package com.example.PTicketing.repository;

import com.example.PTicketing.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    List<CheckIn> findByEventIdOrderByScannedAtDesc(Long eventId);
    long countByEventId(Long eventId);
}
