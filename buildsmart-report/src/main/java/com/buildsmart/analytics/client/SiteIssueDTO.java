package com.buildsmart.analytics.client;

import java.time.LocalDateTime;

public record SiteIssueDTO(
        String issueId,
        String projectId,
        String logId,
        String description,
        String severity,
        String reportedBy,
        LocalDateTime reportedAt,
        String status,
        String assignedTo,
        String resolutionNotes,
        LocalDateTime resolvedAt
) {
}

