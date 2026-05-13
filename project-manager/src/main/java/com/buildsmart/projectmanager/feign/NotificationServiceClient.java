package com.buildsmart.projectmanager.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the dedicated Notification microservice.
 * Notifications are fire-and-forget: the PM service does not wait for a response.
 *
 * The notification service's POST /api/notifications endpoint is open
 * (no auth required) for service-to-service calls.
 *
 * Eureka service name: notification-service (port 8089).
 * Context path on that service: /api → full URL: /api/notifications
 *
 * Routing model:
 *   • toUserId is REQUIRED — every notification targets exactly one user.
 *   • toRole   is REQUIRED — descriptive tag for filtering / UI rendering.
 *   • fromUserId is optional (omit for system-generated events).
 */
@FeignClient(
        name = "notification-service",
        path = "/api/notifications",
        contextId = "pmNotificationClient"
)
public interface NotificationServiceClient {

    /**
     * Create a notification in the notification service.
     * Called after task assignment, approval, and rejection.
     */
    @PostMapping
    void create(@RequestBody NotificationPayload payload);

    /**
     * Minimal payload matching the notification service's NotificationRequest DTO.
     */
    record NotificationPayload(
            String eventType,        // e.g. "TASK_ASSIGNED", "TASK_COMPLETED", "APPROVAL_REJECTED"
            String message,          // Human-readable message text
            String fromService,      // Originating service name
            String fromRole,         // Role that triggered the event
            String fromUserId,       // UserId that triggered the event (nullable)
            String toRole,           // Role of the recipient (REQUIRED)
            String toUserId,         // UserId of the recipient (REQUIRED)
            String referenceId,      // Task ID, approval ID, etc.
            String payload           // Optional JSON payload for extra data
    ) {}
}