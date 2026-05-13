package com.buildsmart.vendor.entity;

import com.buildsmart.vendor.enums.AssignedTaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a task assigned to this vendor by a Project Manager.
 * Synced from the project-service via TASK_ASSIGNED notifications.
 */
@Entity
@Table(name = "assigned_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uq_pm_task_id_vendor", columnNames = "pm_task_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignedTask {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @Column(name = "pm_task_id", nullable = false, length = 50)
    private String pmTaskId;

    @Column(name = "pm_notification_id", nullable = false, length = 50)
    private String pmNotificationId;

    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    @Column(name = "assigned_to", nullable = false, length = 50)
    private String assignedTo;

    @Column(name = "assigned_by", nullable = false, length = 50)
    private String assignedBy;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssignedTaskStatus status = AssignedTaskStatus.PENDING;

    @Column(name = "linked_entity_id", length = 20)
    private String linkedEntityId;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Populated when PM rejects this task — reason shown back to the vendor. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) syncedAt = LocalDateTime.now();
    }
}
