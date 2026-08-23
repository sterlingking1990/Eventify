package com.example.PTicketing.service;

import com.example.PTicketing.dto.request.AssignScannerRequest;
import com.example.PTicketing.dto.response.EventScannerResponse;
import com.example.PTicketing.entity.Event;
import com.example.PTicketing.entity.EventScanner;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.enums.UserRole;
import com.example.PTicketing.exception.BadRequestException;
import com.example.PTicketing.exception.DuplicateResourceException;
import com.example.PTicketing.exception.ResourceNotFoundException;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.repository.EventRepository;
import com.example.PTicketing.repository.EventScannerRepository;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lets an organizer decide whose scanner login opens their event's gate.
 *
 * <p>CheckInService refuses a scan unless the account is this event's organizer,
 * an assigned scanner, or an admin. Everything here exists to feed that check:
 * without it, SCANNER accounts are either useless (no way to grant access) or
 * dangerous (access to every event).
 */
@Service
@RequiredArgsConstructor
public class EventScannerService {

    private final EventScannerRepository eventScannerRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<EventScannerResponse> listScanners(Long eventId, Long requesterId) {
        assertOrganizer(eventId, requesterId);
        return eventScannerRepository.findByEventId(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventScannerResponse assignScanner(Long eventId, AssignScannerRequest request, Long organizerId) {
        Event event = loadEventForOrganizer(eventId, organizerId);
        User scanner = resolveUser(request);

        if (scanner.getRole() != UserRole.SCANNER) {
            throw new BadRequestException(
                    "Account " + describe(scanner) + " does not have the SCANNER role");
        }

        if (eventScannerRepository.existsByEventIdAndUserId(event.getId(), scanner.getId())) {
            throw new DuplicateResourceException(
                    "Scanner " + describe(scanner) + " is already assigned to this event");
        }

        EventScanner assignment = eventScannerRepository.save(EventScanner.builder()
                .event(event)
                .user(scanner)
                .build());

        return toResponse(assignment);
    }

    @Transactional
    public void removeScanner(Long eventId, Long userId, Long requesterId) {
        assertOrganizer(eventId, requesterId);
        EventScanner assignment = eventScannerRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("This scanner is not assigned to the event"));
        eventScannerRepository.delete(assignment);
    }

    /**
     * Shared by CheckInService: the one rule that decides whether an account may
     * scan for an event. Admins pass everywhere for platform support; organizers
     * only on their own events; scanners only where assigned; everyone else never.
     */
    public void assertAllowedToScan(User account, Event event) {
        switch (account.getRole()) {
            case ADMIN -> { }
            case ORGANIZER -> {
                if (!event.getOrganizer().getId().equals(account.getId())) {
                    throw new UnauthorizedException("You are not the organizer of this event");
                }
            }
            case SCANNER -> {
                if (!eventScannerRepository.existsByEventIdAndUserId(event.getId(), account.getId())) {
                    throw new UnauthorizedException("This account is not assigned to scan for this event");
                }
            }
            default -> throw new UnauthorizedException("This account cannot scan tickets");
        }
    }

    private Event loadEventForOrganizer(Long eventId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new UnauthorizedException("Only the event organizer can manage scanners");
        }
        return event;
    }

    private void assertOrganizer(Long eventId, Long requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!event.getOrganizer().getId().equals(requesterId)) {
            throw new UnauthorizedException("Only the event organizer can manage scanners");
        }
    }

    private User resolveUser(AssignScannerRequest request) {
        boolean hasId = request.getUserId() != null;
        boolean hasIdentifier = request.getIdentifier() != null && !request.getIdentifier().isBlank();

        if (hasId == hasIdentifier) {
            // Both or neither: ambiguous in the first case, unusable in the second.
            throw new BadRequestException("Provide exactly one of userId or identifier");
        }

        if (hasId) {
            return userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("No user with id " + request.getUserId()));
        }

        return userRepository.findByEmail(request.getIdentifier())
                .or(() -> userRepository.findByScannerUsername(request.getIdentifier()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found for identifier " + request.getIdentifier()));
    }

    private String describe(User user) {
        return user.getScannerUsername() != null ? user.getScannerUsername() : user.getEmail();
    }

    private EventScannerResponse toResponse(EventScanner assignment) {
        User scanner = assignment.getUser();
        return EventScannerResponse.builder()
                .id(assignment.getId())
                .userId(scanner.getId())
                .fullName(scanner.getFullName())
                .scannerUsername(scanner.getScannerUsername())
                .phone(scanner.getPhone())
                .assignedAt(assignment.getCreatedAt())
                .build();
    }
}
