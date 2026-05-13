package com.buildsmart.projectmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String notificationId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "notification_from", length = 50)
    private String notificationFrom;

    @Column(name = "notification_to", length = 50)
    private String notificationTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_task_id")
    private ProjectTask relatedTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_approval_id")
    private ApprovalRequest relatedApproval;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_milestone_id")
    private ProjectMilestone relatedMilestone;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
