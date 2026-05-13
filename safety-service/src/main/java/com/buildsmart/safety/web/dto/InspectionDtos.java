package com.buildsmart.safety.web.dto;

import com.buildsmart.safety.domain.model.InspectionStatus;
import com.buildsmart.safety.domain.model.InspectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class InspectionDtos {

    public record CreateInspectionRequest(
            @NotBlank(message = "projectId is required")
            String projectId,

            @NotNull(message = "inspectionType is required")
            InspectionType inspectionType,

            @NotBlank(message = "findings is required")
            @Size(min = 20, max = 200, message = "findings must be between 20 and 200 characters")
            String findings,

            /**
             * Optional: local AssignedTask ID (e.g. "SAT001") to link this
             * inspection to a PM-assigned task.
             * When provided the service validates:
             * (1) the task exists, (2) it belongs to the current officer,
             * (3) its projectId matches, (4) it is still PENDING.
             */
            String assignedTaskId
    ) {}

    public record InspectionResponse(
            String inspectionId,
            String projectId,
            LocalDate date,
            String officerId,
            String officerName,
            InspectionType inspectionType,
            String findings,
            InspectionStatus status,
            String assignedTaskId
    ) {}
}
