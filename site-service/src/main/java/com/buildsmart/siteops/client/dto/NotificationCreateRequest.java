package com.buildsmart.siteops.client.dto;

/**
 * Outbound payload for POST /api/notifications on the central Notification service.
 * Mirrors com.company.notification.dto.NotificationRequest.
 *
 * Routing rules (the central service enforces these as @NotBlank):
 *   • toUserId is REQUIRED — every notification targets exactly one user.
 *   • toRole   is REQUIRED — descriptive tag for filtering and UI rendering.
 *   • fromUserId is optional (omit for system-generated events).
 *
 * Field order matches NotificationRequest in notification-service so Jackson
 * deserialises correctly on the wire.
 */
public record NotificationCreateRequest(
        String eventType,
        String message,
        String fromService,
        String fromRole,
        String fromUserId,
        String toRole,
        String toUserId,
        String referenceId,
        String payload
) {}