package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class AnalyticsResponse {
    private long totalTicketsSold;
    private long totalCheckIns;
    private double checkInPercentage;
    private BigDecimal totalRevenue;
    private BigDecimal totalFees;
    private BigDecimal netRevenue;
    private Map<String, Long> ticketsByType;
    private List<DailySales> dailySales;

    @Data
    @Builder
    @AllArgsConstructor
    public static class DailySales {
        private String date;
        private long ticketsSold;
        private BigDecimal revenue;
    }
}
