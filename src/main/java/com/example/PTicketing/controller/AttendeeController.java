package com.example.PTicketing.controller;

import com.example.PTicketing.dto.response.AttendeeResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.AttendeeService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An organiser's guest list for one event.
 *
 * <p>The role check here is necessary but not sufficient — every method also passes
 * the caller's id to the service, which verifies they own the event. Without that,
 * any organiser could read another's buyer list by changing the id in the URL.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/attendees")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "List everyone holding a ticket to this event",
               description = "One row per ticket, newest first. Optional `search` matches " +
                             "name, email, phone, order reference or ticket code.")
    public ResponseEntity<List<AttendeeResponse>> getAttendees(
            @PathVariable Long eventId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(attendeeService.getAttendees(eventId, user.getId(), search));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Download the guest list as CSV",
               description = "For printing or handing to door staff. Excludes ticket codes, " +
                             "since a printed list should not be a set of working tickets.")
    public ResponseEntity<byte[]> exportAttendees(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails user) {

        String csv = attendeeService.exportCsv(eventId, user.getId());

        // UTF-8 BOM so Excel renders ₦ and non-ASCII names correctly instead of
        // falling back to the system codepage and mangling them.
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendees-event-" + eventId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
