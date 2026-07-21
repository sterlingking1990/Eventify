package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class ReferralLinkResponse {
    private Long id;
    private String code;
    private String eventTitle;
    private Long eventId;
    private int clicks;
    private int ticketsSold;
    private LocalDateTime createdAt;
}
