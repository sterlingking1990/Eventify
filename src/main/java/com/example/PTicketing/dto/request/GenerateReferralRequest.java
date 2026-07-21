package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateReferralRequest {

    @NotNull
    private Long eventId;
}
