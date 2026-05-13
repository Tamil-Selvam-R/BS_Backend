package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.enums.IssueStatus;
import jakarta.validation.constraints.Size;

/**
 * Partial update: only description, status, assignedTo, resolutionNotes,
 * allocationId, and resourceId can change.
 * Severity cannot be changed after creation (audit trail).
 *
 * When the PM provides allocationId + resourceId, the issue is automatically
 * marked RESOLVED.
 */
public record IssueUpdateRequest(

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        IssueStatus status,

        String assignedTo,

        @Size(max = 2000, message = "Resolution notes must not exceed 2000 characters")
        String resolutionNotes,

        /** Resource Allocation ID from the Resource Allocation service — triggers auto-RESOLVED. */
        String allocationId,

        /** Resource ID from the Resource Allocation service — triggers auto-RESOLVED. */
        String resourceId
) {}
