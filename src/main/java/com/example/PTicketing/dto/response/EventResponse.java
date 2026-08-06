package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.EventStatus;
import com.example.PTicketing.enums.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String flyerImage;
    private CategoryResponse category;
    private EventType type;
    private EventStatus status;
    private String venue;
    private Double latitude;
    private Double longitude;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String slug;
    private String organizerName;
    private Long organizerId;
    private List<TicketTypeResponse> ticketTypes;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;
    private LocalDateTime createdAt;
}
