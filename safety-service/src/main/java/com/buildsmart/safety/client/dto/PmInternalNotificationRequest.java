package com.buildsmart.safety.client.dto;

import java.time.LocalDateTime;

/**
 * Request body sent to PM service POST /api/notifications/internal.
 * Shape matches PM's NotificationResponse so it maps directly to the
 * notifications table without any field translation.
 */
public record PmInternalNotificationRequest(
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
