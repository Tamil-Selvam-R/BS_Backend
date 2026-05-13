package com.buildsmart.siteops.client;

import com.buildsmart.siteops.client.dto.ProjectDto;
import com.buildsmart.siteops.client.fallback.ProjectManagerClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for the Project Manager service.
 * Uses Eureka service name "project-service" — no hardcoded URL.
 *
 * PM has server.servlet.context-path=/api, so all PM endpoints need the /api prefix.
 * The site engineer's JWT is forwarded so PM can authenticate via its JWT filter.
 * Resilience4j fallback is handled by ProjectManagerClientFallbackFactory.
 */
@FeignClient(name = "project-service", contextId = "siteOpsProjectManagerClient", fallbackFactory = ProjectManagerClientFallbackFactory.class)
public interface ProjectManagerClient {

    @GetMapping("/api/projects/{projectId}")
    ProjectDto getProject(
            @PathVariable("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);

    @PostMapping("/api/notifications/siteops/site-log-submitted")
    void notifyPMSiteLogSubmitted(
            @RequestBody SiteLogNotificationPayload payload,
            @RequestHeader("Authorization") String authorization);

    @PostMapping("/api/notifications/siteops/issue-submitted")
    void notifyPMIssueSubmitted(
            @RequestBody IssueNotificationPayload payload,
            @RequestHeader("Authorization") String authorization);

    /**
     * Create a formal ApprovalRequest in the Project Manager service.
     * Used by the SiteLog submit flow so daily site logs show up at
     * GET /api/approvals on the PM side, ready for approve/reject.
     *
     * Mirrors the pattern used by Vendor's submitInvoice flow.
     */
    @PostMapping("/api/approvals")
    Object createApprovalRequest(
            @RequestBody ApprovalCreateRequest payload,
            @RequestHeader("Authorization") String authorization);

    /**
     * Push the project's cumulative progressPercent (from the latest site log)
     * to PM so it can recompute the project's milestone statuses.
     *
     * Body: {@code { "progressPercent": 35.5 }}
     *
     * PM endpoint: POST /api/projects/{projectId}/milestones/progress
     */
    @PostMapping("/api/projects/{projectId}/milestones/progress")
    Object updateMilestonesByProgress(
            @PathVariable("projectId") String projectId,
            @RequestBody java.util.Map<String, java.math.BigDecimal> body,
            @RequestHeader("Authorization") String authorization);

    // ── Inner payload records ─────────────────────────────────────────────────

    record SiteLogNotificationPayload(
            String logId,
            String projectId,
            String submittedBy,
            String approvalId,
            String activitiesSummary
    ) {}

    record IssueNotificationPayload(
            String issueId,
            String projectId,
            String logId,
            String reportedBy,
            String severity,
            String description,
            String approvalId
    ) {}

    /**
     * Mirror of PM's CreateApprovalRequest DTO. PM requires a valid
     * (projectId, taskId) pair so the approval can be linked to a project task.
     * For SiteLog submissions we pass any active SE task on that project as
     * taskId (the local AssignedTask holds the pmTaskId).
     */
    record ApprovalCreateRequest(
            String projectId,
            String taskId,
            String approvalId,
            String approvalType,           // "SITE_WORK" for site-log submissions
            String description,
            Double amount,
            String requestedBy,
            String requestedByDepartment   // "SITE_ENGINEER"
    ) {}
}
