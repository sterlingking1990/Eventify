package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class EventStatsResponse {
    private long totalTickets;
    private long checkedIn;
    private long remaining;
    private double checkInPercentage;
    private long totalRevenue;
}
