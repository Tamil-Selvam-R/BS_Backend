package com.buildsmart.safety.web.dto;

import java.util.List;

public record SafetyPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
