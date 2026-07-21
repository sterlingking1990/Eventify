package com.example.PTicketing.controller;

import com.example.PTicketing.dto.response.TicketResponse;
import com.example.PTicketing.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final OrderService orderService;

    @GetMapping("/my-tickets")
    public ResponseEntity<List<TicketResponse>> getMyTickets(@RequestParam String email) {
        return ResponseEntity.ok(orderService.getUserTickets(email));
    }
}
