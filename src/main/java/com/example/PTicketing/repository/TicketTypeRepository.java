package com.example.PTicketing.repository;

import com.example.PTicketing.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    List<TicketType> findByEventId(Long eventId);

    /**
     * Atomically reserves {@code quantity} tickets, but only if that many remain.
     *
     * <p>Returns 1 on success, 0 when there is not enough stock left.
     *
     * <p>Read-modify-write on ticketsSold loses updates when two buyers confirm
     * at the same moment, which is exactly what happens when a popular event goes
     * on sale. Pushing the check into the UPDATE's WHERE clause makes the
     * database serialise it for us.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
           update TicketType t
              set t.ticketsSold = t.ticketsSold + :quantity
            where t.id = :id
              and t.quantity - t.ticketsSold >= :quantity
           """)
    int reserveQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
           update TicketType t
              set t.ticketsSold = t.ticketsSold - :quantity
            where t.id = :id
              and t.ticketsSold >= :quantity
           """)
    int releaseQuantity(@Param("id") Long id, @Param("quantity") int quantity);
}
