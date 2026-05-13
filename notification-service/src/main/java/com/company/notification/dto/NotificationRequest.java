package com.company.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Inbound payload for POST /notifications.
 * Sent by other microservices via Feign.
 *
 * Routing rules:
 *   • toUserId is REQUIRED — every notification must target exactly one user.
 *   • toRole is REQUIRED as a descriptive tag — used by the UI to render
 *     the right icon/label and to enable role-based filtering on the bell.
 *
 * The producer is responsible for resolving the recipient (and ideally the
 * sender) before calling this endpoint. This service stays generic and never
 * does user lookups.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NotificationRequest {

    @NotBlank
    @Size(max = 64)
    private String eventType;

    @NotBlank
    @Size(max = 1000)
    private String message;

    @Size(max = 64)
    private String fromService;

    @Size(max = 64)
    private String fromRole;

    /**
     * Sender userId. Optional — pass null for system-generated events.
     */
    @Size(max = 64)
    private String fromUserId;

    /**
     * Recipient role — REQUIRED. Acts as a descriptive tag for filtering;
     * does not by itself determine who sees the notification.
     */
    @NotBlank
    @Size(max = 64)
    private String toRole;

    /**
     * Recipient userId — REQUIRED. The primary routing key.
     * Only this user will see this notification.
     */
    @NotBlank
    @Size(max = 64)
    private String toUserId;

    @Size(max = 128)
    private String referenceId;

    /** Optional JSON string. Service stores as-is. */
    private String payload;
}