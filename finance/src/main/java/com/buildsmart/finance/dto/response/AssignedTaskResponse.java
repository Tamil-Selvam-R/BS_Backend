package com.buildsmart.finance.dto.response;

import com.buildsmart.finance.entity.enums.AssignedTaskStatus;

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
