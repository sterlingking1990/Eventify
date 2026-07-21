package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private UserRole role;
    private String avatarUrl;
    private String phone;
}
