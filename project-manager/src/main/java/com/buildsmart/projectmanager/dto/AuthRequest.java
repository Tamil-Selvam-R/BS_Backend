package com.buildsmart.projectmanager.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AuthResponse {

    private String token;
    private String userId;
    private String name;
    private String email;
    private String role;
    private String departmentCode;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UserResponse {

    private String userId;
    private String name;
    private String email;
    private String role;
    private String roleDisplayName;
    private String departmentCode;
    private String departmentDisplayName;
    private Boolean isActive;
}
