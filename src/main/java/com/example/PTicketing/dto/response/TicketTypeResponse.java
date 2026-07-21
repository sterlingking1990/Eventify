package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class TicketTypeResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private int quantity;
    private int ticketsSold;
    private boolean isFree;
    private int maxPerOrder;
    private LocalDateTime salesStartDate;
    private LocalDateTime salesEndDate;
}
