package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Payout;
import com.example.PTicketing.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<Payout> findByStatus(PayoutStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payout p WHERE p.user.id = :userId AND p.status = :status")
    BigDecimal sumAmountByUserIdAndStatus(@Param("userId") Long userId, @Param("status") PayoutStatus status);
}
