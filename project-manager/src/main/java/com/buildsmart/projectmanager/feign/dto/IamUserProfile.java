package com.buildsmart.projectmanager.feign.dto;

public record IamUserProfile(
        String userId,
        String name,
        String email,
        String phone,
        String role,
        String status
) {
}

