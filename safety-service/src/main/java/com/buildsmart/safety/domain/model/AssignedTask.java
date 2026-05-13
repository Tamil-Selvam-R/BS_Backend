package com.buildsmart.safety.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a task assigned to this safety officer by a Project Manager.
 * Synced from the project-service via TASK_ASSIGNED notifications.
 * <p>
 * Rules:
 * - A safety officer can only create inspections for projects that have
 *   assigned tasks (for them).
 * - An inspection linked to this task marks it as COMPLETED.
 * - Only one inspection may be linked per task.
 */
@Entity
@Table(name = "assigned_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uq_pm_task_id", columnNames = "pm_task_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignedTask {

    @Id
    @Column(name = "id", length = 20)
    private String id;                        // e.g. SAT001

    /** Task ID in the project-service. */
    @Column(name = "pm_task_id", nullable = false, length = 50)
    private String pmTaskId;

    /** Notification ID in the project-service (used to avoid re-syncing). */
    @Column(name = "pm_notification_id", nullable = false, length = 50)
    private String pmNotificationId;

    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    /** Safety officer userId (the person this task is assigned to). */
    @Column(name = "assigned_to", nullable = false, length = 50)
    private String assignedTo;

    /** Project Manager userId who created the task. */
    @Column(name = "assigned_by", nullable = false, length = 50)
    private String assignedBy;

    /** Full task description as provided by the PM service. */
    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssignedTaskStatus status = AssignedTaskStatus.PENDING;

    /** Inspection ID that completed this task (set when inspection → COMPLETED). */
    @Column(name = "linked_inspection_id", length = 20)
    private String linkedInspectionId;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Populated when PM rejects this task — reason shown back to the officer. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) syncedAt = LocalDateTime.now();
    }
}
