package com.buildsmart.analytics.client;

import java.time.LocalDate;

public record MilestoneDTO(
        String milestoneId,
        String projectId,
        String name,
        String description,
        Integer order,
        String status,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        Integer daysRemaining,
        Boolean isOverdue
) {}
