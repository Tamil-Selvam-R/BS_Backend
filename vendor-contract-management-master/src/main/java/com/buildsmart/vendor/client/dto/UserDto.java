package com.buildsmart.vendor.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO that maps user data returned by the IAM service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(
        String userId,
        String name,
        String email,
        String role,
        String status
) {}

