package com.buildsmart.vendor.dto.response;

import com.buildsmart.vendor.enums.DocumentStatus;
import com.buildsmart.vendor.enums.DocumentType;

import java.time.LocalDateTime;

public class DocumentResponse {
    private String documentId;
    private String vendorId;
    private String approvalId;
    private String taskId;
    private String projectId;
    private String contractId;
    private String documentName;
    private DocumentType documentType;
    private String filePath;
    private Long fileSize;
    private String uploadedBy;
    private String description;
    private LocalDateTime uploadedAt;
    private DocumentStatus status;
    private String approvedBy;
    private String rejectedBy;
    private String rejectionReason;

    public DocumentResponse() {
    }

    public DocumentResponse(String documentId, String vendorId, String documentName, DocumentType documentType,
                       String filePath, Long fileSize, String uploadedBy, String description,
                       LocalDateTime uploadedAt, DocumentStatus status, String approvedBy, String rejectedBy,
                       String rejectionReason) {
        this.documentId = documentId;
        this.vendorId = vendorId;
        this.documentName = documentName;
        this.documentType = documentType;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
        this.description = description;
        this.uploadedAt = uploadedAt;
        this.status = status;
        this.approvedBy = approvedBy;
        this.rejectedBy = rejectedBy;
        this.rejectionReason = rejectionReason;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}


