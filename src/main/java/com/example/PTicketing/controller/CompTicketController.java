package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.IssueCompTicketRequest;
import com.example.PTicketing.dto.response.CompTicketResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.CompTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events/{eventId}/complimentary")
@RequiredArgsConstructor
public class CompTicketController {

    private final CompTicketService compTicketService;

    @PostMapping
    public ResponseEntity<CompTicketResponse> issueCompTicket(
            @PathVariable Long eventId,
            @Valid @RequestBody IssueCompTicketRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(compTicketService.issueCompTicket(eventId, request, user.getId()));
    }

    @GetMapping
    public ResponseEntity<List<CompTicketResponse>> getCompTickets(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(compTicketService.getCompTickets(eventId, user.getId()));
    }
}
