package com.buildsmart.analytics.client;

import java.time.LocalDateTime;

/**
 * Mirror of vendor module's DocumentResponse (com.buildsmart.vendor.dto.response.DocumentResponse).
 * Used by report-analytics to compute compliance rate per vendor and overall.
 *
 * Status values come from vendor module's DocumentStatus enum:
 *   PENDING, SUBMITTED, APPROVED, REJECTED.
 */
public record VendorDocumentDTO(
        String documentId,
        String vendorId,
        String approvalId,
        String taskId,
        String projectId,
        String contractId,
        String documentName,
        String documentType,
        String filePath,
        Long fileSize,
        String uploadedBy,
        String description,
        LocalDateTime uploadedAt,
        String status,
        String approvedBy,
        String rejectedBy,
        String rejectionReason
) {}
