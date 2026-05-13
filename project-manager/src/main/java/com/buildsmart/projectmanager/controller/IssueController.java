package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.feign.SiteOpsClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PM-side Issue Management endpoints.
 *
 * The PM can:
 *  - View all issues reported by site officers for a project
 *  - Resolve an issue by providing an allocationId + resourceId
 *    (SiteOps auto-marks it RESOLVED when both are present)
 */
/**
 * Mapped under /projects/issues so it lives behind the existing API-gateway
 * route {@code /api/projects/**} → project-service. The "/issues" base would
 * otherwise collide with siteops's {@code /api/issues/**} route at the gateway.
 */
@RestController
@RequestMapping("/projects/issues")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Issues (PM)", description = "PM view and resolution of SiteOps issues")
public class IssueController {

    private final SiteOpsClient siteOpsClient;

    /**
     * GET /api/issues?projectId=PRJ001
     * Returns all issues for the given project from the SiteOps service.
     */
    @GetMapping
    @Operation(summary = "Get all issues for a project",
               description = "Fetches issues from the SiteOps service for the given projectId.")
    public ResponseEntity<List<SiteOpsClient.IssueDto>> getIssuesByProject(
            @RequestParam @NotBlank String projectId) {
        List<SiteOpsClient.IssueDto> issues = siteOpsClient.getIssuesByProject(projectId);
        return ResponseEntity.ok(issues);
    }

    /**
     * POST /api/issues/{issueId}/resolve
     * PM resolves an issue by allocating a resource.
     * Provide allocationId and resourceId; SiteOps will set status to RESOLVED.
     *
     * Request body:
     * {
     *   "allocationId": "ALLOC001",
     *   "resourceId":   "RES001",
     *   "resolutionNotes": "Crane allocated for 3 days"  // optional
     * }
     */
    @PostMapping("/{issueId}/resolve")
    @Operation(summary = "Resolve an issue by allocating a resource",
               description = "PM provides allocationId + resourceId to resolve the issue in SiteOps.")
    public ResponseEntity<SiteOpsClient.IssueDto> resolveIssue(
            @PathVariable String issueId,
            @RequestBody Map<String, String> body) {

        String allocationId    = body.get("allocationId");
        String resourceId      = body.get("resourceId");
        String resolutionNotes = body.get("resolutionNotes");
        String assignedTo      = body.get("assignedTo");

        if (allocationId == null || allocationId.isBlank()) {
            throw new IllegalArgumentException("allocationId is required to resolve an issue.");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId is required to resolve an issue.");
        }

        SiteOpsClient.ResolveIssueRequest request = new SiteOpsClient.ResolveIssueRequest(
                "RESOLVED",   // explicit status hint (siteops will also auto-set from allocationId+resourceId)
                assignedTo,
                resolutionNotes,
                allocationId,
                resourceId,
                null          // description unchanged
        );

        SiteOpsClient.IssueDto resolved = siteOpsClient.resolveIssue(issueId, request);
        log.info("PM resolved issue '{}' with allocationId='{}', resourceId='{}'",
                issueId, allocationId, resourceId);
        return ResponseEntity.ok(resolved);
    }
}
