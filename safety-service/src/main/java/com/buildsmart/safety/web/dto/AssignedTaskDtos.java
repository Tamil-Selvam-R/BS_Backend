package com.buildsmart.safety.web.dto;

import com.buildsmart.safety.domain.model.AssignedTaskStatus;

import java.time.LocalDateTime;
import java.util.List;

public class AssignedTaskDtos {

    /**
     * Response returned for every assigned task record.
     */
    public record AssignedTaskResponse(
            String id,
            String pmTaskId,
            String pmNotificationId,
            String projectId,
            String assignedTo,
            String assignedBy,
            String description,
            AssignedTaskStatus status,
            String linkedInspectionId,
            LocalDateTime syncedAt,
            LocalDateTime completedAt,
            /** Populated when PM rejected the most recent submission. */
            String rejectionReason
    ) {}

    /**
     * Result of a sync operation — how many new tasks were pulled from PM.
     */
    public record SyncResult(
            int newTasksSynced,
            int alreadyExisted,
            List<AssignedTaskResponse> newTasks
    ) {}
}
