package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class CompTicketResponse {
    private Long id;
    private String recipientEmail;
    private String recipientName;
    private String qrCode;
    private TicketStatus status;
    private String issuedBy;
    private LocalDateTime createdAt;
}
