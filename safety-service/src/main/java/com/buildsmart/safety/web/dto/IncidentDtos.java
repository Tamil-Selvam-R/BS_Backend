package com.buildsmart.safety.web.dto;

import com.buildsmart.safety.domain.model.IncidentSeverity;
import com.buildsmart.safety.domain.model.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class IncidentDtos {

    public record CreateIncidentRequest(
            @NotBlank(message = "projectId is required")
            String projectId,

            @NotBlank(message = "description is required")
            @Size(max = 5000, message = "description must not exceed 5000 characters")
            String description,

            @NotNull(message = "severity is required")
            IncidentSeverity severity
    ) {}

    public record IncidentResponse(
            String incidentId,
            String projectId,
            LocalDate date,
            String description,
            IncidentSeverity severity,
            String reportedBy,
            String reportedByName,
            IncidentStatus status
    ) {}
}
