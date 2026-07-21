package com.example.PTicketing.controller;

import com.example.PTicketing.dto.request.SubscribeRequest;
import com.example.PTicketing.dto.request.UnsubscribeRequest;
import com.example.PTicketing.dto.response.ApiResponse;
import com.example.PTicketing.service.NewsletterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/newsletter")
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        newsletterService.subscribe(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Subscribed successfully"));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        newsletterService.unsubscribe(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Unsubscribed successfully"));
    }
}
