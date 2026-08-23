package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.AssignScannerRequest;
import com.example.PTicketing.dto.response.ApiResponse;
import com.example.PTicketing.dto.response.EventScannerResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.EventScannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events/{eventId}/scanners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class EventScannerController {

    private final EventScannerService eventScannerService;

    @GetMapping
    public ResponseEntity<List<EventScannerResponse>> listScanners(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(eventScannerService.listScanners(eventId, user.getId()));
    }

    @PostMapping
    public ResponseEntity<EventScannerResponse> assignScanner(
            @PathVariable Long eventId,
            @Valid @RequestBody AssignScannerRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventScannerService.assignScanner(eventId, request, user.getId()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse> removeScanner(
            @PathVariable Long eventId,
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails user) {
        eventScannerService.removeScanner(eventId, userId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Scanner removed from event"));
    }
}
