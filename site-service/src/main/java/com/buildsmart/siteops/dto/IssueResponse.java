package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.IssueStatus;
import com.buildsmart.siteops.enums.ResourceType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IssueResponse(
        String issueId,
        String projectId,
        String logId,
        String description,
        IssueSeverity severity,
        String reportedBy,
        LocalDateTime reportedAt,
        IssueStatus status,
        String assignedTo,
        String resolutionNotes,
        LocalDateTime resolvedAt,
        String approvalId,
        // Resource request (nullable – only present when SE requested resources)
        ResourceType resourceType,
        String resourceDescription,
        LocalDate resourceFromDate,
        LocalDate resourceToDate,
        // PM resolution fields (nullable – set when PM allocates a resource)
        String allocationId,
        String resourceId
) {}


