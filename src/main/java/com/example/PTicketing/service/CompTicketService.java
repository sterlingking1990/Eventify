package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.IssueCompTicketRequest;
import com.example.PTicketing.dto.response.CompTicketResponse;
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
public class CompTicketService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;

    @Transactional
    public CompTicketResponse issueCompTicket(Long eventId, IssueCompTicketRequest request, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to issue comp tickets for this event");
        }

        User issuer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String qrText = java.util.UUID.randomUUID().toString();

        com.example.PTicketing.entity.TicketType tt = ticketTypeRepository.findByEventId(eventId)
                .stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Event has no ticket types configured"));

        com.example.PTicketing.entity.Ticket ticket = com.example.PTicketing.entity.Ticket.builder()
                .event(event)
                .ticketType(tt)
                .buyerEmail(request.getRecipientEmail())
                .buyerName(request.getRecipientName())
                .qrCode(qrText)
                .status(TicketStatus.ACTIVE)
                .build();

        ticket = ticketRepository.save(ticket);

        return CompTicketResponse.builder()
                .id(ticket.getId())
                .recipientEmail(request.getRecipientEmail())
                .recipientName(request.getRecipientName())
                .qrCode(ticket.getQrCode())
                .status(ticket.getStatus())
                .issuedBy(issuer.getFullName())
                .createdAt(ticket.getPurchasedAt())
                .build();
    }

    public List<CompTicketResponse> getCompTickets(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (!event.getOrganizer().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized");
        }

        return ticketRepository.findByEventId(eventId)
                .stream()
                .filter(t -> t.getOrder() == null)
                .map(t -> CompTicketResponse.builder()
                        .id(t.getId())
                        .recipientEmail(t.getBuyerEmail())
                        .recipientName(t.getBuyerName())
                        .qrCode(t.getQrCode())
                        .status(t.getStatus())
                        .issuedBy(event.getOrganizer().getFullName())
                        .createdAt(t.getPurchasedAt())
                        .build())
                .toList();
    }
}
