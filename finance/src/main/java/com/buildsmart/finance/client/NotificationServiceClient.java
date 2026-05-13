package com.buildsmart.finance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the dedicated notification microservice (FEATURE SET 6).
 *
 * Finance pushes the following events fire-and-forget:
 *   - BUDGET_SUBMITTED / BUDGET_APPROVED / BUDGET_REJECTED
 *   - EXPENSE_SUBMITTED / EXPENSE_APPROVED / EXPENSE_REJECTED
 *   - PAYMENT_CREATED  / PAYMENT_RELEASED
 *   - TASK_SUBMITTED / TASK_COMPLETED / TASK_REJECTED (finance tasks)
 *
 * Every payload MUST carry a non-blank toUserId — the central service
 * enforces @NotBlank on it. Resolve the recipient before calling this
 * client; do not pass null.
 *
 * The notification service exposes POST /api/notifications without auth so
 * service-to-service calls do not need a JWT.
 */
@FeignClient(
        name = "notification-service",
        contextId = "financeNotificationClient",
        fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    @PostMapping("/api/notifications")
    void create(@RequestBody NotificationPayload payload);

    /**
     * Mirror of the notification-service NotificationRequest DTO. Field names
     * and order MUST stay identical so Jackson maps each field correctly on
     * the wire.
     *
     * Per-user routing fields:
     *   - fromUserId : userId that triggered the event (nullable for system events)
     *   - toUserId   : userId of the recipient (REQUIRED)
     *
     * The toRole field stays as a descriptive tag for the recipient's UI.
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