package com.buildsmart.projectmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "project_tasks", uniqueConstraints = {
    @UniqueConstraint(columnNames = "taskId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 15)
    private String taskId;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private DepartmentCode assignedDepartment;

    @Column(nullable = false, length = 50)
    private String assignedTo;

    @Column(name = "assigned_by", length = 120)
    private String assignedBy;

    @Column(nullable = false)
    private LocalDate plannedStart;

    @Column(nullable = false)
    private LocalDate plannedEnd;

    private LocalDate actualStart;

    private LocalDate actualEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = TaskStatus.PENDING;
        }
    }
}
