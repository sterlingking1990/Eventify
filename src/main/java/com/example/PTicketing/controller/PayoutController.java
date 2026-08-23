package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.OtpRequest;
import com.example.PTicketing.dto.request.PayoutRequest;
import com.example.PTicketing.dto.response.BalanceResponse;
import com.example.PTicketing.dto.response.FeeInfoResponse;
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

    /**
     * The bank list, so the organiser picks a code rather than typing a name.
     * Supplying that code is what lets the account be verified before an
     * irreversible transfer.
     */
    @GetMapping("/banks")
    public ResponseEntity<Object> listBanks() {
        return ResponseEntity.ok(payoutService.listBanks());
    }

    @PutMapping("/{payoutId}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutResponse> processPayout(
            @PathVariable Long payoutId,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(payoutService.approvePayout(payoutId, admin.getId()));
    }

    @PutMapping("/{payoutId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutResponse> rejectPayout(
            @PathVariable Long payoutId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(payoutService.rejectPayout(payoutId, admin.getId(), reason));
    }

    /** Submits the OTP Paystack sent to Brandible's registered contact, completing the transfer. */
    @PutMapping("/{payoutId}/submit-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutResponse> submitOtp(
            @PathVariable Long payoutId,
            @Valid @RequestBody OtpRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(payoutService.submitOtp(payoutId, request.getOtp(), admin.getId()));
    }

    @PutMapping("/{payoutId}/resend-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutResponse> resendOtp(
            @PathVariable Long payoutId,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(payoutService.resendOtp(payoutId, admin.getId()));
    }

    /** The current cashout fee % and admin-response SLA, so the organiser sees them before requesting. */
    @GetMapping("/fee-info")
    public ResponseEntity<FeeInfoResponse> getFeeInfo() {
        return ResponseEntity.ok(payoutService.getFeeInfo());
    }
}
