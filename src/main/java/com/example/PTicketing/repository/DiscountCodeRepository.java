package com.example.PTicketing.repository;

import com.example.PTicketing.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {
    Optional<DiscountCode> findByCode(String code);
    List<DiscountCode> findByEventId(Long eventId);
    Optional<DiscountCode> findByCodeAndEventId(String code, Long eventId);
}
