package com.buildsmart.projectmanager.dto;

import com.buildsmart.projectmanager.entity.DepartmentCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new task for a project")
public class CreateTaskRequest {

    @Schema(description = "Description of the task", example = "Review construction plans")
    private String description;

    @NotNull(message = "Assigned department is required")
    @Schema(description = "Role/Department to assign the task to (matches IAM roles)", example = "SITE_ENGINEER")
    private DepartmentCode assignedDepartment;

    @NotBlank(message = "Assigned user ID is required")
    @Schema(description = "User ID of the person assigned to the task (from IAM service)", example = "USR001")
    private String assignedTo;

    @NotNull(message = "Planned start date is required")
    @Schema(description = "Planned start date", example = "2026-04-20")
    private LocalDate plannedStart;

    @NotNull(message = "Planned end date is required")
    @Schema(description = "Planned end date", example = "2026-04-25")
    private LocalDate plannedEnd;

    @Schema(description = "Actual start date", example = "2026-04-20")
    private LocalDate actualStart;

    @Schema(description = "Actual end date", example = "2026-04-25")
    private LocalDate actualEnd;
}
