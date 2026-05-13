package com.buildsmart.siteops.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(
        String userId,
        String name,
        String email,
        String role,
        String status
) {}

