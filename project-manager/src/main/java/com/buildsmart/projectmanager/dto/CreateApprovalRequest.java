package com.buildsmart.projectmanager.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApprovalRequest {

    @NotBlank(message = "Project ID is required")
    private String projectId;

    @NotBlank(message = "Task ID is required")
    private String taskId;

    private String approvalId;

    @NotBlank(message = "Approval type is required")
    @Pattern(regexp = "^(BUDGET|VENDOR|SITE_WORK|SAFETY)$", message = "Invalid approval type")
    private String approvalType;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String description;

    @PositiveOrZero(message = "Amount must be zero or positive")
    private Double amount;

    private String requestedBy;
    private String requestedByDepartment;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ApprovalActionRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(APPROVE|REJECT)$", message = "Action must be APPROVE or REJECT")
    private String action;

    @Size(max = 1000, message = "Rejection reason cannot exceed 1000 characters")
    private String rejectionReason;
}
