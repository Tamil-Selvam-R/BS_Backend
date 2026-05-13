package com.buildsmart.siteops.controller;

import com.buildsmart.siteops.dto.IssueRequest;
import com.buildsmart.siteops.dto.IssueResponse;
import com.buildsmart.siteops.dto.IssueUpdateRequest;
import com.buildsmart.siteops.dto.PaginatedResponse;
import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.IssueStatus;
import com.buildsmart.siteops.security.AuthenticationHelper;
import com.buildsmart.siteops.service.IssueService;
import com.buildsmart.siteops.util.PaginationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private static final String DEFAULT_ISSUE_SORT = "reportedAt";
    private static final Set<String> ALLOWED_ISSUE_SORT_FIELDS = Set.of(
            "reportedAt", "severity", "status", "projectId", "issueId", "resolvedAt"
    );

    private final IssueService issueService;
    private final AuthenticationHelper authenticationHelper;

    @PostMapping
    public ResponseEntity<IssueResponse> create(
            @Valid @RequestBody IssueRequest request,
            @RequestHeader("Authorization") String authorization) {

        // Extract reportedBy directly from JWT token (with module validation)
        String reportedBy = authenticationHelper.extractUserIdFromHeader(authorization);

        IssueResponse created = issueService.createIssue(request, reportedBy, authorization);
        return ResponseEntity
                .created(URI.create("/api/issues/" + created.issueId()))
                .body(created);
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<IssueResponse> getById(@PathVariable String issueId) {
        return ResponseEntity.ok(issueService.getIssueById(issueId));
    }

    /**
     * PATCH /api/issues/{issueId}
     * Project Manager assigns, updates status, or closes an issue.
     * RESTRICTED: Only PROJECT_MANAGER and ADMIN roles can call this.
     */
    @PatchMapping("/{issueId}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<IssueResponse> update(
            @PathVariable String issueId,
            @Valid @RequestBody IssueUpdateRequest request) {
        return ResponseEntity.ok(issueService.updateIssue(issueId, request));
    }

    @GetMapping
    public ResponseEntity<List<IssueResponse>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssueSeverity severity,
            @RequestParam(required = false) String reportedBy) {

        List<IssueResponse> result;
        if (status != null) {
            result = issueService.getIssuesByProjectAndStatus(projectId, status);
        } else if (severity != null) {
            result = issueService.getIssuesByProjectAndSeverity(projectId, severity);
        } else if (reportedBy != null) {
            result = issueService.getIssuesByProjectAndReporter(projectId, reportedBy);
        } else {
            result = issueService.getIssuesByProject(projectId);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/issues/paginated/list
     * Get paginated issues for a project.
     * Query params: projectId (required), pageNumber (default 0), pageSize (default 10),
     * sortBy (default reportedAt), sortDirection (default DESC)
     */
    @GetMapping("/paginated/list")
    public ResponseEntity<PaginatedResponse<IssueResponse>> listPaginated(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "reportedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        String safeSortBy = PaginationUtil.normalizeSortBy(sortBy, ALLOWED_ISSUE_SORT_FIELDS, DEFAULT_ISSUE_SORT);

        PaginatedResponse<IssueResponse> result =
                issueService.getIssuesByProjectPaginated(projectId, pageNumber, pageSize, safeSortBy, sortDirection);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/issues/paginated/by-status
     * Get paginated issues filtered by status.
     */
    @GetMapping("/paginated/by-status")
    public ResponseEntity<PaginatedResponse<IssueResponse>> listByStatusPaginated(
            @RequestParam String projectId,
            @RequestParam IssueStatus status,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "reportedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        String safeSortBy = PaginationUtil.normalizeSortBy(sortBy, ALLOWED_ISSUE_SORT_FIELDS, DEFAULT_ISSUE_SORT);

        PaginatedResponse<IssueResponse> result =
                issueService.getIssuesByProjectAndStatusPaginated(projectId, status, pageNumber, pageSize, safeSortBy, sortDirection);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/issues/paginated/by-severity
     * Get paginated issues filtered by severity.
     */
    @GetMapping("/paginated/by-severity")
    public ResponseEntity<PaginatedResponse<IssueResponse>> listBySeverityPaginated(
            @RequestParam String projectId,
            @RequestParam IssueSeverity severity,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "reportedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        String safeSortBy = PaginationUtil.normalizeSortBy(sortBy, ALLOWED_ISSUE_SORT_FIELDS, DEFAULT_ISSUE_SORT);

        PaginatedResponse<IssueResponse> result =
                issueService.getIssuesByProjectAndSeverityPaginated(projectId, severity, pageNumber, pageSize, safeSortBy, sortDirection);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/issues/paginated/by-reporter
     * Get paginated issues filtered by reporter.
     */
    @GetMapping("/paginated/by-reporter")
    public ResponseEntity<PaginatedResponse<IssueResponse>> listByReporterPaginated(
            @RequestParam String projectId,
            @RequestParam String reportedBy,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "reportedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        String safeSortBy = PaginationUtil.normalizeSortBy(sortBy, ALLOWED_ISSUE_SORT_FIELDS, DEFAULT_ISSUE_SORT);

        PaginatedResponse<IssueResponse> result =
                issueService.getIssuesByProjectAndReporterPaginated(projectId, reportedBy, pageNumber, pageSize, safeSortBy, sortDirection);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-log/{logId}")
    public ResponseEntity<List<IssueResponse>> byLog(@PathVariable String logId) {
        return ResponseEntity.ok(issueService.getIssuesByLogId(logId));
    }
}
