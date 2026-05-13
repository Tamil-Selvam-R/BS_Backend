package com.buildsmart.siteops.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IAMApiResponse<T>(
        boolean success,
        String message,
        T data
) {}

