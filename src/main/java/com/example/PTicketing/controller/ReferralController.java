package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.GenerateReferralRequest;
import com.example.PTicketing.dto.response.ReferralLinkResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.ReferralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @PostMapping("/generate")
    public ResponseEntity<ReferralLinkResponse> generateReferral(
            @Valid @RequestBody GenerateReferralRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referralService.generateReferral(user.getId(), request.getEventId()));
    }

    @GetMapping
    public ResponseEntity<List<ReferralLinkResponse>> getMyReferrals(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(referralService.getMyReferrals(user.getId()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ReferralLinkResponse> getReferralStats(@PathVariable String code) {
        return ResponseEntity.ok(referralService.getReferralStats(code));
    }

    @PostMapping("/{code}/click")
    public ResponseEntity<Void> trackClick(@PathVariable String code) {
        referralService.trackClick(code);
        return ResponseEntity.ok().build();
    }
}
