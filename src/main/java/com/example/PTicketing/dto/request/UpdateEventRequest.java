package com.example.PTicketing.dto.request;

import com.example.PTicketing.enums.EventStatus;
import com.example.PTicketing.enums.EventType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UpdateEventRequest {

    private String title;
    private String description;
    private String flyerImage;
    private Long categoryId;
    private EventType type;
    private EventStatus status;
    private String venue;
    private Double latitude;
    private Double longitude;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;
}
