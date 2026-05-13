package com.buildsmart.analytics.client;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectDTO(
        String projectId,
        String projectName,
        String description,
        String templateId,
        String templateName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Double budget,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer totalMilestones,
        Integer completedMilestones,
        Integer totalTasks,
        Integer completedTasks
) {}