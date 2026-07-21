package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthRequest {

    @NotBlank
    private String googleId;

    @NotBlank
    private String email;

    @NotBlank
    private String fullName;

    private String avatarUrl;
}
