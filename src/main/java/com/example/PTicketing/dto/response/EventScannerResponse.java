package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class EventScannerResponse {
    private Long id;
    private Long userId;
    private String fullName;
    private String scannerUsername;
    private String phone;
    private LocalDateTime assignedAt;
}
