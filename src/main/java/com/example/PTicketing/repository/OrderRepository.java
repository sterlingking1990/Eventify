package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Order;
import com.example.PTicketing.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
