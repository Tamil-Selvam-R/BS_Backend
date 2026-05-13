package com.company.notification.dto;

import com.company.notification.model.Notification;
import lombok.*;

import java.time.Instant;

/**
 * Outbound payload returned by GET /notifications and POST /notifications.
 *
 * Includes both fromUserId and toUserId so the UI can render "from Priya"
 * and confirm the notification is for the current user.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private String eventType;
    private String message;
    private String fromService;
    private String fromRole;
    private String fromUserId;
    private String toRole;
    private String toUserId;
    private String referenceId;
    private boolean read;
    private String payload;
    private Instant createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .eventType(n.getEventType())
                .message(n.getMessage())
                .fromService(n.getFromService())
                .fromRole(n.getFromRole())
                .fromUserId(n.getFromUserId())
                .toRole(n.getToRole())
                .toUserId(n.getToUserId())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .payload(n.getPayload())
                .createdAt(n.getCreatedAt())
                .build();
    }
}