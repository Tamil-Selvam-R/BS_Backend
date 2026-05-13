package com.buildsmart.vendor.client.dto;

public class PMClientApprovalRequest {
    private String approvalId;
    private String projectId;
    private String taskId;

    private String approvalType;
    private String description;
    private Double amount;
    private String requestedBy;
    private String requestedByDepartment;

    public PMClientApprovalRequest() {
    }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }



    public String getApprovalType() { return approvalType; }
    public void setApprovalType(String approvalType) { this.approvalType = approvalType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getRequestedByDepartment() { return requestedByDepartment; }
    public void setRequestedByDepartment(String requestedByDepartment) { this.requestedByDepartment = requestedByDepartment; }
}
