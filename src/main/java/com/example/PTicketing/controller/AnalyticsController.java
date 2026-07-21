package com.example.PTicketing.controller;

import com.example.PTicketing.dto.response.AnalyticsResponse;
import com.example.PTicketing.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<AnalyticsResponse> getEventAnalytics(@PathVariable Long eventId) {
        return ResponseEntity.ok(analyticsService.getEventAnalytics(eventId));
    }
}
