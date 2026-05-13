package com.buildsmart.projectmanager.dto;

import com.buildsmart.projectmanager.entity.ApprovalRequest;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalResponse {

    private String approvalId;
    private String projectId;
    private String projectName;
    private String taskId;
    private String taskDescription;
    private String requestedBy;
    private String requestedByName;
    private String requestedByDepartment;
    private LocalDateTime requestedAt;
    private String approvalType;
    private String status;
    private String description;
    private Double amount;
    private String approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    public static ApprovalResponse fromEntity(ApprovalRequest entity) {
        if (entity == null) {
            return null;
        }
        
        return ApprovalResponse.builder()
                .approvalId(entity.getApprovalId())
                .projectId(entity.getProject() != null ? entity.getProject().getProjectId() : null)
                .projectName(entity.getProject() != null ? entity.getProject().getProjectName() : null)
                .taskId(entity.getTask() != null ? entity.getTask().getTaskId() : null)
                .taskDescription(entity.getTask() != null ? entity.getTask().getDescription() : null)
                .requestedBy(entity.getRequestedByName())
                .requestedByName(entity.getRequestedByName())
                .requestedByDepartment(entity.getRequestedByDepartment() != null ? 
                        entity.getRequestedByDepartment().name() : null)
                .requestedAt(entity.getRequestedAt())
                .approvalType(entity.getApprovalType() != null ? entity.getApprovalType().name() : null)
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .description(entity.getDescription())
                .amount(entity.getAmount())
                .approvedByName(null)
                .approvedAt(entity.getApprovedAt())
                .rejectionReason(entity.getRejectionReason())
                .build();
    }
}
