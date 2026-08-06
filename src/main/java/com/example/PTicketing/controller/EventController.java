package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.CreateEventRequest;
import com.example.PTicketing.dto.request.TicketTypeRequest;
import com.example.PTicketing.dto.request.UpdateEventRequest;
import com.example.PTicketing.dto.response.ApiResponse;
import com.example.PTicketing.dto.response.EventListResponse;
import com.example.PTicketing.dto.response.EventResponse;
import com.example.PTicketing.dto.response.TicketTypeResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventListResponse>> getAllEvents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(eventService.getAllEvents(category, type, search));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<EventResponse> getEventBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(eventService.getEventBySlugPublic(slug));
    }

    @GetMapping("/my-events")
    public ResponseEntity<List<EventListResponse>> getMyEvents(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(eventService.getMyEvents(user.getId()));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(request, user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(eventService.updateEvent(id, request, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        eventService.deleteEvent(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Event cancelled successfully"));
    }

    @PostMapping("/{eventId}/ticket-types")
    public ResponseEntity<TicketTypeResponse> createTicketType(
            @PathVariable Long eventId,
            @Valid @RequestBody TicketTypeRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createTicketType(eventId, request, user.getId()));
    }

    @GetMapping("/{eventId}/ticket-types")
    public ResponseEntity<List<TicketTypeResponse>> getTicketTypes(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getTicketTypes(eventId));
    }

    @DeleteMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public ResponseEntity<ApiResponse> deleteTicketType(
            @PathVariable Long eventId,
            @PathVariable Long ticketTypeId,
            @AuthenticationPrincipal CustomUserDetails user) {
        eventService.deleteTicketType(eventId, ticketTypeId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Ticket type deleted"));
    }
}
