package com.example.PTicketing.dto.request;

import com.example.PTicketing.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateEventRequest {

    @NotBlank
    private String title;

    private String description;

    private String flyerImage;

    private Long categoryId;

    @NotNull
    private EventType type;

    private String venue;

    private Double latitude;

    private Double longitude;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    private List<TicketTypeRequest> ticketTypes;
}
