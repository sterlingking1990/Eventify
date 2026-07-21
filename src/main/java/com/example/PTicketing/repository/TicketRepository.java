package com.example.PTicketing.repository;

import com.example.PTicketing.entity.Ticket;
import com.example.PTicketing.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByOrderId(Long orderId);
    List<Ticket> findByEventId(Long eventId);
    List<Ticket> findByBuyerEmail(String email);
    Optional<Ticket> findByQrCode(String qrCode);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
    List<Ticket> findByEventIdAndStatus(Long eventId, TicketStatus status);
}
