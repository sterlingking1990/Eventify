package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private String ticketTypeName;
    private String eventTitle;
    private String eventSlug;
    private String buyerEmail;
    private String buyerName;
    private String qrCode;
    private TicketStatus status;
    private LocalDateTime purchasedAt;
    private LocalDateTime checkedInAt;
}
