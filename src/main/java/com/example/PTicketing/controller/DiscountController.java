package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.CreateDiscountRequest;
import com.example.PTicketing.dto.response.ApiResponse;
import com.example.PTicketing.dto.response.DiscountCodeResponse;
import com.example.PTicketing.exception.UnauthorizedException;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.DiscountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events/{eventId}/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;

    @PostMapping
    public ResponseEntity<DiscountCodeResponse> createDiscount(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateDiscountRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(discountService.createDiscount(eventId, request, user.getId()));
    }

    @GetMapping
    public ResponseEntity<List<DiscountCodeResponse>> getDiscounts(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) throw new UnauthorizedException("Authentication required");
        return ResponseEntity.ok(discountService.getDiscounts(eventId, user.getId()));
    }

    @DeleteMapping("/{discountId}")
    public ResponseEntity<ApiResponse> deleteDiscount(
            @PathVariable Long eventId,
            @PathVariable Long discountId,
            @AuthenticationPrincipal CustomUserDetails user) {
        discountService.deleteDiscount(discountId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Discount deleted"));
    }
}
