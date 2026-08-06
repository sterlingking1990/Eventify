package com.example.PTicketing.service;

import com.example.PTicketing.dto.response.CheckInResponse;
import com.example.PTicketing.dto.response.EventStatsResponse;
import com.example.PTicketing.entity.*;
import com.example.PTicketing.enums.TicketStatus;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public CheckInResponse scanTicket(String qrCode, Long scannerUserId) {
        Ticket ticket = ticketRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new BadRequestException("Invalid QR code: ticket not found"));

        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            return CheckInResponse.builder()
                    .success(false)
                    .message("Ticket already " + ticket.getStatus().name().toLowerCase())
                    .qrCode(qrCode)
                    .build();
        }

        User scanner = userRepository.findById(scannerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Scanner not found"));

        ticket.setStatus(TicketStatus.USED);
        ticket.setCheckedInAt(java.time.LocalDateTime.now());
        ticketRepository.save(ticket);

        CheckIn checkIn = CheckIn.builder()
                .ticket(ticket)
                .event(ticket.getEvent())
                .scannedBy(scanner)
                .build();

        checkIn = checkInRepository.save(checkIn);

        return CheckInResponse.builder()
                .id(checkIn.getId())
                .qrCode(ticket.getQrCode())
                .ticketTypeName(ticket.getTicketType().getName())
                .buyerEmail(ticket.getBuyerEmail())
                .buyerName(ticket.getBuyerName())
                .scannedBy(scanner.getFullName())
                .eventTitle(ticket.getEvent().getTitle())
                .scannedAt(checkIn.getScannedAt())
                .success(true)
                .message("Check-in successful")
                .build();
    }

    public List<CheckInResponse> getCheckInLogs(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Only the event organizer can view check-in logs");
        }

        List<CheckIn> checkIns = checkInRepository.findByEventIdOrderByScannedAtDesc(eventId);
        return checkIns.stream().map(this::toResponse).toList();
    }

    public EventStatsResponse getEventStats(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        long totalTickets = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.ACTIVE)
                + ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.USED);

        long checkedIn = checkInRepository.countByEventId(eventId);
        long remaining = totalTickets - checkedIn;
        double percentage = totalTickets > 0 ? (checkedIn * 100.0 / totalTickets) : 0;

        return EventStatsResponse.builder()
                .totalTickets(totalTickets)
                .checkedIn(checkedIn)
                .remaining(remaining)
                .checkInPercentage(Math.round(percentage * 100.0) / 100.0)
                .build();
    }

    private CheckInResponse toResponse(CheckIn checkIn) {
        Ticket ticket = checkIn.getTicket();
        return CheckInResponse.builder()
                .id(checkIn.getId())
                .qrCode(ticket.getQrCode())
                .ticketTypeName(ticket.getTicketType().getName())
                .buyerEmail(ticket.getBuyerEmail())
                .buyerName(ticket.getBuyerName())
                .scannedBy(checkIn.getScannedBy().getFullName())
                .eventTitle(checkIn.getEvent().getTitle())
                .scannedAt(checkIn.getScannedAt())
                .success(true)
                .message("Checked in")
                .build();
    }
}
