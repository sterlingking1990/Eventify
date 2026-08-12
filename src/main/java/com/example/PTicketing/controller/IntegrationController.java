package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.CreateExternalOrderRequest;
import com.example.PTicketing.dto.response.ExternalOrderResponse;
import com.example.PTicketing.service.IntegrationOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Server-to-server endpoints for external sales channels.
 *
 * <p>Authenticated by {@code X-Service-Key}, not a user token — see
 * {@code ServiceKeyAuthFilter}. Not for browser use.
 */
@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE')")
@Tag(name = "Integration", description = "Server-to-server ticket sales for external channels")
public class IntegrationController {

    private final IntegrationOrderService integrationOrderService;

    @PostMapping("/orders")
    @Operation(summary = "Create a pending order and hold its tickets",
               description = "The caller supplies the Paystack reference it will charge against. " +
                             "Tickets are reserved immediately and released if payment does not arrive.")
    public ResponseEntity<ExternalOrderResponse> createOrder(
            @Valid @RequestBody CreateExternalOrderRequest request) {
        return ResponseEntity.ok(integrationOrderService.createOrder(request));
    }

    @PostMapping("/orders/{reference}/confirm")
    @Operation(summary = "Confirm payment and issue tickets",
               description = "Idempotent. Repeat calls return the tickets already issued with " +
                             "newlyIssued=false, so a retried webhook cannot mint twice.")
    public ResponseEntity<ExternalOrderResponse> confirmPayment(@PathVariable String reference) {
        return ResponseEntity.ok(integrationOrderService.confirmPayment(reference));
    }

    @GetMapping("/orders/{reference}")
    @Operation(summary = "Look up an order by its Paystack reference",
               description = "Use to re-fetch tickets when channel delivery failed.")
    public ResponseEntity<ExternalOrderResponse> getOrder(@PathVariable String reference) {
        return ResponseEntity.ok(integrationOrderService.getByReference(reference));
    }
}
