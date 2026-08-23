package com.example.PTicketing.dto.request;

import lombok.Data;

@Data
public class AssignScannerRequest {

    /** Id of the existing SCANNER account to assign. One identifier is required. */
    private Long userId;

    /** Alternative when the id is unknown: matched against email, then scanner username. */
    private String identifier;
}
