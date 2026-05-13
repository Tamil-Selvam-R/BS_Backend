package com.buildsmart.projectmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Received from other microservices (e.g. safety-service) to insert a
 * notification directly into this service's database so the recipient
 * sees it in GET /notifications/me.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalNotificationRequest {
    private String notificationId;
    private String projectId;
    private String type;
    private String title;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private String notificationFrom;
    private String notificationTo;
    private String relatedTaskId;
    private String relatedApprovalId;
    private String relatedMilestoneId;
}
