package com.buildsmart.projectmanager.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

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
