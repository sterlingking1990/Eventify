package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.EventStatus;
import com.example.PTicketing.enums.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class EventListResponse {
    private Long id;
    private String title;
    private String description;
    private String flyerImage;
    private String categoryName;
    private EventType type;
    private EventStatus status;
    private String venue;
    private LocalDateTime startDate;
    private String slug;
    private String organizerName;
    private BigDecimal minPrice;
}
