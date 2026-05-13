package com.buildsmart.projectmanager.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign client for the SiteOps microservice.
 * Used by the PM to view and resolve issues reported by site officers.
 *
 * Eureka service name: siteops-service.
 * Issues are at /api/issues on that service.
 */
@FeignClient(
        name = "siteops-service",
        path = "/api/issues",
        contextId = "pmSiteOpsClient"
)
public interface SiteOpsClient {

    /**
     * Fetch all issues for a given project from SiteOps.
     */
    @GetMapping
    List<IssueDto> getIssuesByProject(@RequestParam("projectId") String projectId);

    /**
     * Resolve an issue: PM provides allocationId + resourceId.
     * SiteOps will auto-set status to RESOLVED when both are present.
     */
    @PatchMapping("/{issueId}")
    IssueDto resolveIssue(
            @PathVariable("issueId") String issueId,
            @RequestBody ResolveIssueRequest request);

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record ResolveIssueRequest(
            String status,
            String assignedTo,
            String resolutionNotes,
            String allocationId,
            String resourceId,
            String description
    ) {}

    record IssueDto(
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
            LocalDateTime resolvedAt,
            String approvalId,
            String resourceType,
            String resourceDescription,
            LocalDate resourceFromDate,
            LocalDate resourceToDate,
            String allocationId,
            String resourceId
    ) {}
}
