package com.example.PTicketing.controller;

import com.example.PTicketing.dto.response.CheckInResponse;
import com.example.PTicketing.dto.response.EventStatsResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.CheckInService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/check-in")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('SCANNER', 'ORGANIZER')")
    public ResponseEntity<CheckInResponse> scanTicket(
            @RequestParam String qrCode,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(checkInService.scanTicket(qrCode, user.getId()));
    }

    @GetMapping("/events/{eventId}/logs")
    @PreAuthorize("hasAnyRole('SCANNER', 'ORGANIZER')")
    public ResponseEntity<List<CheckInResponse>> getCheckInLogs(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(checkInService.getCheckInLogs(eventId, user.getId()));
    }

    @GetMapping("/events/{eventId}/stats")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<EventStatsResponse> getEventStats(
            @PathVariable Long eventId) {
        return ResponseEntity.ok(checkInService.getEventStats(eventId));
    }
}
