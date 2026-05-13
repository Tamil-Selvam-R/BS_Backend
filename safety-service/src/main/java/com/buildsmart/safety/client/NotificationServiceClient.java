package com.buildsmart.safety.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the dedicated Notification microservice (FEATURE SET 6).
 *
 * The notification service exposes POST /api/notifications without auth so
 * other services can fire-and-forget events. Calls are non-blocking — failures
 * are absorbed by the fallback so the originating safety operation is never
 * rolled back due to a notification problem.
 *
 * Routing model:
 *   • toUserId is REQUIRED — every notification targets exactly one user.
 *   • toRole   is REQUIRED — descriptive tag for filtering / UI.
 *   • fromUserId is optional (omit for system-generated events).
 */
@FeignClient(
        name = "notification-service",
        contextId = "safetyNotificationClient",
        fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    @PostMapping("/api/notifications")
    void create(@RequestBody NotificationPayload payload);

    /**
     * Payload mirrors the notification-service's NotificationRequest DTO.
     * Field names MUST match exactly so Jackson serialization on the wire works.
     */
    record NotificationPayload(
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
}