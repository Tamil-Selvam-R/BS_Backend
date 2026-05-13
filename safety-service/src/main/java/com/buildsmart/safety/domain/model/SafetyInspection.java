package com.buildsmart.safety.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "safety_inspections")
@Getter
@Setter
public class SafetyInspection {

    @Id
    @Column(name = "inspection_id", length = 20)
    private String inspectionId;

    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    @Column(name = "officer_id", nullable = false, length = 20)
    private String officerId;

    @Column(name = "officer_name", length = 100)
    private String officerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false)
    private InspectionType inspectionType;

    @Column(name = "findings", nullable = false, length = 200)
    private String findings;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InspectionStatus status;

    /**
     * Optional: the AssignedTask (local safety DB ID) that this inspection is
     * fulfilling. When the inspection reaches COMPLETED, the linked task is
     * automatically marked COMPLETED too.
     */
    @Column(name = "assigned_task_id", length = 20)
    private String assignedTaskId;
}
