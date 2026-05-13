package com.buildsmart.safety.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "incidents")
@Getter
@Setter
public class Incident {

    @Id
    @Column(name = "incident_id", length = 20)
    private String incidentId;

    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private IncidentSeverity severity;

    @Column(name = "reported_by", nullable = false, length = 20)
    private String reportedBy;

    @Column(name = "reported_by_name", length = 100)
    private String reportedByName;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status;
}
