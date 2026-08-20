package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Payout;
import com.example.PTicketing.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<Payout> findByStatus(PayoutStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payout p WHERE p.user.id = :userId AND p.status = :status")
    BigDecimal sumAmountByUserIdAndStatus(@Param("userId") Long userId, @Param("status") PayoutStatus status);

    /**
     * Moves a payout between states, but only from the expected one.
     *
     * <p>Returns 1 when this caller won the transition, 0 when it had already moved.
     * A read-then-write status check does not survive two admins working the same
     * queue, or one double-clicking — both would observe PENDING and both act. With
     * real money leaving an account, the database has to decide.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Payout p
              set p.status = :to,
                  p.processedAt = :at,
                  p.processedBy = (select u from User u where u.id = :adminId)
            where p.id = :payoutId
              and p.status = :from
           """)
    int claimStatus(@Param("payoutId") Long payoutId,
                    @Param("from") PayoutStatus from,
                    @Param("to") PayoutStatus to,
                    @Param("adminId") Long adminId,
                    @Param("at") java.time.LocalDateTime at);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payout p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PayoutStatus status);
}
