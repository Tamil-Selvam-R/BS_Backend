package com.buildsmart.finance.client.dto;

import java.time.LocalDateTime;

/**
 * Maps the NotificationResponse returned by the project-service.
 * Used when Finance polls PM's GET /api/notifications/to/{userId}
 * to discover TASK_ASSIGNED notifications for the logged-in finance officer.
 */
public record PmNotificationDto(
        String notificationId,
        String projectId,
        String type,
        String title,
        String message,
        Boolean isRead,
        LocalDateTime createdAt,
        String notificationFrom,
        String notificationTo,
        String relatedTaskId,
        String relatedApprovalId,
        String relatedMilestoneId
) {}
