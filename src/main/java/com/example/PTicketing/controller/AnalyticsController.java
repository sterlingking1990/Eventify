package com.example.PTicketing.controller;

import com.example.PTicketing.dto.response.AnalyticsResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * The role check is necessary but not sufficient — the caller's id is passed
     * through so the service can verify they own this event. Previously any
     * organiser could read another's revenue by changing the id in the URL.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<AnalyticsResponse> getEventAnalytics(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(analyticsService.getEventAnalytics(eventId, user.getId()));
    }
}
