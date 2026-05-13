package com.buildsmart.siteops.client.dto;

import java.time.LocalDate;

/**
 * Minimal projection of a Project returned by the Project Manager service.
 * Used to validate projectId and check start/end date boundaries.
 */
public record ProjectDto(
        String projectId,
        String projectName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String createdBy
) {}

