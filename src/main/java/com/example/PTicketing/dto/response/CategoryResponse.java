package com.example.PTicketing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
}
