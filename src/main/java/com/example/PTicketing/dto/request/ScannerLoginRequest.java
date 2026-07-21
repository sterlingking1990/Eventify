package com.example.PTicketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScannerLoginRequest {

    @NotBlank
    private String scannerUsername;

    @NotBlank
    private String password;
}
