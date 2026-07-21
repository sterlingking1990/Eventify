package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Payout;
import com.example.PTicketing.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<Payout> findByStatus(PayoutStatus status);

    BigDecimal sumAmountByUserIdAndStatus(Long userId, PayoutStatus status);
}
