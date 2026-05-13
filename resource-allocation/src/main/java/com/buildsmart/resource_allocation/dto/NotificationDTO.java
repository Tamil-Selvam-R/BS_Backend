package com.buildsmart.resource_allocation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Inbound payload for POST /api/resource-allocation/notifications.
 *
 * This DTO mirrors the central notification-service's request shape so the
 * RA controller can act as a thin pass-through — the request is forwarded to
 * the central service via Feign with no local persistence.
 *
 * toUserId is REQUIRED. The recipient is always one specific user; RA does
 * not broadcast or route by role.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    @NotBlank
    @Size(max = 64)
    private String eventType;

    @NotBlank
    @Size(min = 10, max = 1000)
    private String message;

    /**
     * UserId that triggered the event. Optional — leave null for system events
     * (e.g. automated allocations from a scheduler).
     */
    @Size(max = 64)
    private String fromUserId;

    /**
     * Role of the sender (FYI for the recipient's UI: "from Project Manager").
     * Optional.
     */
    @Size(max = 64)
    private String fromRole;

    /**
     * Role of the recipient. Required as a descriptive tag — the central
     * service rejects requests without it.
     */
    @NotBlank
    @Size(max = 64)
    private String toRole;

    /**
     * UserId of the recipient. REQUIRED — the central service enforces this
     * as @NotBlank and the row will not be visible to anyone if missing.
     */
    @NotBlank
    @Size(max = 64)
    private String toUserId;

    /**
     * Business identifier this notification refers to (allocationId,
     * resourceId, projectId, etc.). Optional but recommended for deep-linking.
     */
    @Size(max = 128)
    private String referenceId;

    /**
     * Optional JSON string passed through to the central service unchanged.
     */
    private String payload;
}