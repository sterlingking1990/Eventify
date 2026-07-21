package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.InitializeOrderRequest;
import com.example.PTicketing.dto.response.OrderResponse;
import com.example.PTicketing.dto.response.TicketResponse;
import com.example.PTicketing.security.CustomUserDetails;
import com.example.PTicketing.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/initialize")
    public ResponseEntity<OrderResponse> initializeOrder(
            @Valid @RequestBody InitializeOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        Long userId = user != null ? user.getId() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.initializeOrder(request, userId));
    }

    @GetMapping("/verify")
    public ResponseEntity<OrderResponse> verifyPayment(@RequestParam String reference) {
        return ResponseEntity.ok(orderService.verifyPayment(reference));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(orderService.getUserOrders(user.getId()));
    }

    @GetMapping("/{orderRef}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderRef) {
        return ResponseEntity.ok(orderService.getOrderByRef(orderRef));
    }
}
