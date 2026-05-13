package com.buildsmart.vendor.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Typed view of PM's ApprovalResponse, narrowed to the fields the vendor
 * module needs for status reconciliation. Marked {@code ignoreUnknown} so PM
 * can add new fields without breaking deserialisation here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PMApprovalDTO {
    private String approvalId;
    private String status;
    private String approvedBy;
    private String approvedByName;
    private String rejectionReason;

    public PMApprovalDTO() {
    }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
