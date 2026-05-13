package com.buildsmart.vendor.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Generic wrapper that mirrors the IAM service's standard API response envelope.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IAMApiResponse<T>(
        boolean success,
        String message,
        T data
) {}

