package com.buildsmart.projectmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Task response")
public class TaskResponse {

    private String taskId;
    private String projectId;
    private String description;
    private String assignedDepartment;
    @Schema(description = "User ID of the assigned user (from IAM service)", example = "USR001")
    private String assignedTo;
    @Schema(description = "User ID of the project manager who assigned the task", example = "BSVM001")
    private String assignedBy;
    private LocalDate plannedStart;
    private LocalDate plannedEnd;
    private LocalDate actualStart;
    private LocalDate actualEnd;
    private String status;
    /** Populated when the latest approval was rejected — shows the PM's rejection remarks. */
    private String rejectionReason;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UpdateTaskStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
