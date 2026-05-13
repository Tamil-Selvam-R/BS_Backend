package com.buildsmart.finance.client.dto;

public record UserDto(
        String userId,
        String name,
        String email,
        String role,
        String status
) {}