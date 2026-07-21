package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnsubscribeRequest {

    @NotBlank
    @Email
    private String email;
}
