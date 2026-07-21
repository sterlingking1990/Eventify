package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketTypeRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private BigDecimal price;

    @NotNull
    private Integer quantity;

    private boolean isFree;

    private Integer maxPerOrder;

    private LocalDateTime salesStartDate;

    private LocalDateTime salesEndDate;
}
