package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.PayoutRequest;
import com.example.PTicketing.dto.response.BalanceResponse;
import com.example.PTicketing.dto.response.PayoutResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @PostMapping("/request")
    public ResponseEntity<PayoutResponse> requestPayout(
            @Valid @RequestBody PayoutRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payoutService.requestPayout(request, user.getId()));
    }

    @GetMapping
    public ResponseEntity<List<PayoutResponse>> getPayoutHistory(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(payoutService.getPayoutHistory(user.getId()));
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(payoutService.getBalance(user.getId()));
    }

    @PutMapping("/{payoutId}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutResponse> processPayout(
            @PathVariable Long payoutId,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(payoutService.processPayout(payoutId, admin.getId()));
    }

    @PutMapping("/{payoutId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutResponse> rejectPayout(
            @PathVariable Long payoutId,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(payoutService.rejectPayout(payoutId, admin.getId()));
    }
}
