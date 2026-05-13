package com.buildsmart.safety.client.dto;

import java.time.LocalDateTime;

/**
 * Maps the NotificationResponse returned by the project-service.
 * Used when the safety service polls PM's GET /notifications/to/{userId}
 * to discover TASK_ASSIGNED notifications for the logged-in safety officer.
 */
public record PmNotificationDto(
        String notificationId,
        String projectId,
        String type,          // e.g. "TASK_ASSIGNED"
        String title,
        String message,
        Boolean isRead,
        LocalDateTime createdAt,
        String notificationFrom,   // PM user who assigned the task
        String notificationTo,     // safety officer userId
        String relatedTaskId,      // PM task ID
        String relatedApprovalId,
        String relatedMilestoneId
) {}
