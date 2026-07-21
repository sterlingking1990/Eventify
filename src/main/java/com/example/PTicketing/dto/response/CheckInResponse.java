package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class CheckInResponse {
    private Long id;
    private String qrCode;
    private String ticketTypeName;
    private String buyerEmail;
    private String buyerName;
    private String scannedBy;
    private String eventTitle;
    private LocalDateTime scannedAt;
    private boolean success;
    private String message;
}
