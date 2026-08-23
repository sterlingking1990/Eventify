package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Payout;
import com.example.PTicketing.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<Payout> findByStatus(PayoutStatus status);
    Optional<Payout> findByReference(String reference);
    List<Payout> findByStatusAndResponseDueAtBeforeAndSlaBreachNotifiedFalse(
            PayoutStatus status, LocalDateTime cutoff);

    /**
     * Transfers stuck waiting on an OTP: {@code processedAt} doubles as the moment
     * the payout entered the OTP flow, since approving is what stamps it.
     */
    List<Payout> findByStatusAndProcessedAtBeforeAndSlaBreachNotifiedFalse(
            PayoutStatus status, LocalDateTime cutoff);

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

    /**
     * Same conditional transition as {@link #claimStatus}, without an admin principal.
     *
     * <p>Used by the webhook and OTP-finalize paths, where the caller isn't
     * necessarily the admin who originally approved the payout — leaves
     * {@code processedBy} as whoever approved it rather than overwriting it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Payout p
              set p.status = :to,
                  p.processedAt = :at
            where p.id = :payoutId
              and p.status = :from
           """)
    int claimStatusSystem(@Param("payoutId") Long payoutId,
                          @Param("from") PayoutStatus from,
                          @Param("to") PayoutStatus to,
                          @Param("at") java.time.LocalDateTime at);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payout p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PayoutStatus status);
}
