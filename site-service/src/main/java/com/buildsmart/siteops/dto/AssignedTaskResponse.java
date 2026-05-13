package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.enums.AssignedTaskStatus;

import java.time.LocalDateTime;

public record AssignedTaskResponse(
        String id,
        String pmTaskId,
        String pmNotificationId,
        String projectId,
        String assignedTo,
        String assignedBy,
        String description,
        AssignedTaskStatus status,
        String linkedEntityId,
        LocalDateTime syncedAt,
        LocalDateTime completedAt,
        /** Populated when PM rejected the most recent submission. */
        String rejectionReason
) {}
