package com.buildsmart.analytics.client;

import java.util.List;

public record SafetyPageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}

