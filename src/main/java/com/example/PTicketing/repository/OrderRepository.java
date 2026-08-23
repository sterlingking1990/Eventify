package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Order;
import com.example.PTicketing.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderRef(String orderRef);
    Optional<Order> findByPaystackReference(String paystackReference);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByEventId(Long eventId);

    /**
     * Atomically moves an order to PAID, but only if it is not already PAID.
     *
     * <p>Returns 1 when this caller won the transition and is therefore the one
     * responsible for minting tickets; 0 when the order was already paid (or does
     * not exist), meaning another caller has it in hand.
     *
     * <p>This exists because Paystack retries webhooks and the Brandible relay
     * adds a second retry source, so confirmation can genuinely arrive twice at
     * once. A read-then-write status check does not survive that — both callers
     * observe PENDING and both mint. The database has to decide the winner.
     */
    /*
     * clearAutomatically is essential, not decoration: a bulk update bypasses the
     * persistence context, so without it a subsequent read returns the cached
     * entity still showing PENDING. Callers must therefore load the order AFTER
     * calling this, never before — anything read earlier is detached by the clear.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Order o
              set o.paymentStatus = com.example.PTicketing.enums.PaymentStatus.PAID,
                  o.paidAt = :paidAt
            where o.paystackReference = :reference
              and o.paymentStatus <> com.example.PTicketing.enums.PaymentStatus.PAID
           """)
    int claimPaid(@Param("reference") String reference, @Param("paidAt") LocalDateTime paidAt);

    @Modifying
    @Query("update Order o set o.paymentStatus = :status where o.paystackReference = :reference")
    int updateStatusByReference(@Param("reference") String reference,
                                @Param("status") PaymentStatus status);

    /**
     * The organiser's share of everything that has become withdrawable.
     *
     * <p>Their share is the buyer's total minus the platform fee. Only orders whose
     * release time has passed are counted — money for an event that has not happened
     * yet must not be withdrawable, or an organiser could take the gate and never
     * run the event.
     *
     * <p>A null releasableAt is treated as held. Orders predating that column have
     * no value and stay locked until backfilled; failing closed is the safe
     * direction when the alternative is releasing money early.
     */
    @Query("""
           select coalesce(sum(o.totalAmount - coalesce(o.feeAmount, 0)), 0)
             from Order o
            where o.event.organizer.id = :userId
              and o.paymentStatus = com.example.PTicketing.enums.PaymentStatus.PAID
              and o.releasableAt is not null
              and o.releasableAt <= :now
           """)
    BigDecimal sumReleasableByOrganizer(@Param("userId") Long userId,
                                        @Param("now") LocalDateTime now);

    /** The organiser's share that exists but has not yet cleared its hold. */
    @Query("""
           select coalesce(sum(o.totalAmount - coalesce(o.feeAmount, 0)), 0)
             from Order o
            where o.event.organizer.id = :userId
              and o.paymentStatus = com.example.PTicketing.enums.PaymentStatus.PAID
              and (o.releasableAt is null or o.releasableAt > :now)
           """)
    BigDecimal sumHeldByOrganizer(@Param("userId") Long userId,
                                  @Param("now") LocalDateTime now);

    /** Everything earned regardless of hold — what the organiser has sold in total. */
    @Query("""
           select coalesce(sum(o.totalAmount - coalesce(o.feeAmount, 0)), 0)
             from Order o
            where o.event.organizer.id = :userId
              and o.paymentStatus = com.example.PTicketing.enums.PaymentStatus.PAID
           """)
    BigDecimal sumEarnedByOrganizer(@Param("userId") Long userId);

    /** When the next tranche unlocks, so the UI can say more than "held". */
    @Query("""
           select min(o.releasableAt)
             from Order o
            where o.event.organizer.id = :userId
              and o.paymentStatus = com.example.PTicketing.enums.PaymentStatus.PAID
              and o.releasableAt is not null
              and o.releasableAt > :now
           """)
    LocalDateTime findNextReleaseAt(@Param("userId") Long userId,
                                    @Param("now") LocalDateTime now);

    /**
     * Pushes the release time later when an event is postponed.
     *
     * <p>Deliberately one-way: {@code releasableAt < :newReleaseAt} means bringing an
     * event forward never unlocks funds early. Already-released money is untouched.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
           update Order o
              set o.releasableAt = :newReleaseAt
            where o.event.id = :eventId
              and o.releasableAt is not null
              and o.releasableAt < :newReleaseAt
           """)
    int pushReleaseDateForEvent(@Param("eventId") Long eventId,
                                @Param("newReleaseAt") LocalDateTime newReleaseAt);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentStatus = :status")
    BigDecimal sumTotalByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(o.feeAmount), 0) FROM Order o WHERE o.paymentStatus = :status")
    BigDecimal sumFeesByStatus(@Param("status") PaymentStatus status);

    /**
     * Channel orders whose WhatsApp delivery may never have landed.
     *
     * <p>Paid, sold through an external channel (anything except 'web'), carrying
     * a phone to message, and confirmed inside the window the reconciliation sweep
     * replays. Web orders are excluded on purpose: their buyers have no WhatsApp
     * session at the far end, so replaying them would fail forever rather than fix
     * anything.
     */
    @Query("""
           select o from Order o
            where o.paymentStatus = com.example.PTicketing.enums.PaymentStatus.PAID
              and o.sourceChannel is not null
              and o.sourceChannel <> 'web'
              and o.buyerPhone is not null
              and o.paidAt >= :from
              and o.paidAt <= :to
           """)
    List<Order> findChannelDeliveryCandidates(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}
