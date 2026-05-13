package com.buildsmart.siteops.service.impl;

import com.buildsmart.siteops.client.NotificationServiceClient;
import com.buildsmart.siteops.client.ProjectManagerClient;
import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import com.buildsmart.siteops.client.dto.ProjectDto;
import com.buildsmart.siteops.exception.ResourceNotFoundException;
import com.buildsmart.siteops.util.IdGeneratorUtil;
import com.buildsmart.siteops.util.PaginationUtil;
import com.buildsmart.siteops.dto.IssueRequest;
import com.buildsmart.siteops.dto.IssueResponse;
import com.buildsmart.siteops.dto.IssueUpdateRequest;
import com.buildsmart.siteops.dto.PaginatedResponse;
import com.buildsmart.siteops.entity.Issue;
import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.IssueStatus;
import com.buildsmart.siteops.repository.IssueRepository;
import com.buildsmart.siteops.repository.SiteLogRepository;
import com.buildsmart.siteops.service.IssueService;
import com.buildsmart.siteops.validator.IssueValidator;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Issue lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local SiteOps NotificationService has
 * been removed — engineers and PMs read their notifications from the central
 * service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private final IssueRepository     issueRepository;
    private final SiteLogRepository   siteLogRepository;
    private final IssueValidator      issueValidator;
    private final ProjectManagerClient projectManagerClient;

    /**
     * Central notification service client — issue events route to the PM by
     * userId (resolved via project.createdBy). Fire-and-forget.
     */
    private final NotificationServiceClient notificationServiceClient;

    /* ════════════════════════════════════════════════════════════
       CREATE
       ════════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public IssueResponse createIssue(IssueRequest request, String reportedBy, String authorization) {

        if (reportedBy == null || reportedBy.isBlank()) {
            throw new IllegalArgumentException("Authenticated user (reportedBy) is required.");
        }

        issueValidator.validate(request);

        // ── Validate project + capture the project for PM userId resolution ──
        ProjectDto project = validateProjectForIssue(request.projectId(), authorization);

        var siteLog = siteLogRepository.findById(request.logId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site log not found: '" + request.logId() + "'. "
                                + "You can only report an issue against an existing site log."));

        if (!siteLog.getProjectId().equals(request.projectId())) {
            throw new IllegalArgumentException(
                    "Project ID mismatch: site log '" + request.logId()
                            + "' belongs to project '" + siteLog.getProjectId()
                            + "', but you submitted project '" + request.projectId() + "'. "
                            + "The issue projectId must match the site log's project.");
        }

        String lastId = issueRepository.findTopByOrderByIssueIdDesc()
                .map(Issue::getIssueId).orElse(null);

        String lastApprovalId = issueRepository.findTopByApprovalIdNotNullOrderByApprovalIdDesc()
                .map(Issue::getApprovalId).orElse(null);

        IssueStatus initialStatus = (request.severity() == IssueSeverity.CRITICAL)
                ? IssueStatus.ESCALATED
                : IssueStatus.OPEN;

        Issue issue = new Issue();
        issue.setIssueId(IdGeneratorUtil.nextIssueId(lastId));
        issue.setProjectId(request.projectId());
        issue.setLogId(request.logId());
        issue.setDescription(request.description());
        issue.setSeverity(request.severity());
        issue.setReportedBy(reportedBy);
        issue.setReportedAt(LocalDateTime.now());
        issue.setStatus(initialStatus);
        issue.setApprovalId(IdGeneratorUtil.nextApprovalId(lastApprovalId));

        if (request.resourceType() != null
                || request.resourceDescription() != null
                || request.resourceFromDate() != null
                || request.resourceToDate() != null) {
            if (request.resourceType() == null)
                throw new IllegalArgumentException("resourceType is required when requesting resources (LABOR or EQUIPMENT).");
            if (request.resourceDescription() == null || request.resourceDescription().isBlank())
                throw new IllegalArgumentException(
                        "resourceDescription is required — describe what you need, e.g. 'Crane for moving ceiling slabs' or '6 labourers for slab flooring'.");
            if (request.resourceFromDate() == null)
                throw new IllegalArgumentException("resourceFromDate is required when requesting resources.");
            if (request.resourceToDate() == null)
                throw new IllegalArgumentException("resourceToDate is required when requesting resources.");
            if (request.resourceToDate().isBefore(request.resourceFromDate()))
                throw new IllegalArgumentException("resourceToDate must be on or after resourceFromDate.");
            issue.setResourceType(request.resourceType());
            issue.setResourceDescription(request.resourceDescription());
            issue.setResourceFromDate(request.resourceFromDate());
            issue.setResourceToDate(request.resourceToDate());
        }

        Issue saved = issueRepository.save(issue);

        // Build a severity-tuned message for the central notification.
        String localMsg = switch (saved.getSeverity()) {
            case CRITICAL -> String.format(
                    "CRITICAL ISSUE [%s] reported on project %s by %s. "
                            + "STOP-WORK condition may apply. Immediate action required! Description: %s",
                    saved.getIssueId(), saved.getProjectId(), saved.getReportedBy(),
                    truncate(saved.getDescription(), 200));
            case HIGH -> String.format(
                    "HIGH severity issue [%s] reported on project %s by %s. "
                            + "Please address within 24 hours. Description: %s",
                    saved.getIssueId(), saved.getProjectId(), saved.getReportedBy(),
                    truncate(saved.getDescription(), 200));
            default -> String.format(
                    "New %s issue [%s] reported on project %s. Description: %s",
                    saved.getSeverity(), saved.getIssueId(), saved.getProjectId(),
                    truncate(saved.getDescription(), 150));
        };

        // ── Notify PM via Feign (writes to PM's local notifications) ─────────
        notifyPMIssueSubmitted(saved, authorization);

        // --- Push ISSUE_REPORTED to central notification-service ---
        // The PM of this project is the recipient. Resolve userId via project.createdBy.
        String pmUserId = project != null ? project.createdBy() : null;
        pushCentral(
                "ISSUE_REPORTED",
                localMsg,
                saved.getReportedBy(),            // fromUserId — the site engineer
                "PROJECT_MANAGER",
                pmUserId,                          // toUserId — project's PM
                saved.getIssueId(),
                authorization);

        return toResponse(saved);
    }

    /* ════════════════════════════════════════════════════════════
       READ
       ════════════════════════════════════════════════════════════ */

    @Override
    @Transactional(readOnly = true)
    public IssueResponse getIssueById(String issueId) {
        return toResponse(find(issueId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getIssuesByProject(String projectId) {
        return issueRepository.findByProjectIdOrderByReportedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getIssuesByProjectAndStatus(String projectId, IssueStatus status) {
        List<IssueResponse> results = issueRepository.findByProjectIdAndStatus(projectId, status)
                .stream().map(this::toResponse).toList();
        if (results.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No issues found for project " + projectId + " with status: " + status);
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getIssuesByProjectAndSeverity(String projectId, IssueSeverity severity) {
        List<IssueResponse> results = issueRepository.findByProjectIdAndSeverity(projectId, severity)
                .stream().map(this::toResponse).toList();
        if (results.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No issues found for project " + projectId + " with severity: " + severity);
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getIssuesByProjectAndReporter(String projectId, String reportedBy) {
        List<IssueResponse> results = issueRepository.findByProjectIdAndReportedBy(projectId, reportedBy)
                .stream().map(this::toResponse).toList();
        if (results.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No issues found for project " + projectId + " reported by user: " + reportedBy);
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getIssuesByLogId(String logId) {
        if (!siteLogRepository.existsById(logId)) {
            throw new ResourceNotFoundException("Site log not found: " + logId);
        }
        List<IssueResponse> results = issueRepository.findByLogId(logId)
                .stream().map(this::toResponse).toList();
        if (results.isEmpty()) {
            throw new ResourceNotFoundException("No issues found for site log: " + logId);
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<IssueResponse> getIssuesByProjectPaginated(
            String projectId, int pageNumber, int pageSize, String sortBy, String sortDirection) {
        Pageable pageable = PaginationUtil.getPageable(pageNumber, pageSize, sortBy, sortDirection);
        Page<Issue> page = issueRepository.findByProjectId(projectId, pageable);

        List<IssueResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.<IssueResponse>builder()
                .content(responses)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLastPage(page.isLast())
                .sortBy(sortBy)
                .sortDirection(PaginationUtil.normalizeSortDirection(sortDirection))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<IssueResponse> getIssuesByProjectAndStatusPaginated(
            String projectId, IssueStatus status, int pageNumber, int pageSize, String sortBy, String sortDirection) {
        Pageable pageable = PaginationUtil.getPageable(pageNumber, pageSize, sortBy, sortDirection);
        Page<Issue> page = issueRepository.findByProjectIdAndStatus(projectId, status, pageable);

        List<IssueResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.<IssueResponse>builder()
                .content(responses)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLastPage(page.isLast())
                .sortBy(sortBy)
                .sortDirection(PaginationUtil.normalizeSortDirection(sortDirection))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<IssueResponse> getIssuesByProjectAndSeverityPaginated(
            String projectId, IssueSeverity severity, int pageNumber, int pageSize, String sortBy, String sortDirection) {
        Pageable pageable = PaginationUtil.getPageable(pageNumber, pageSize, sortBy, sortDirection);
        Page<Issue> page = issueRepository.findByProjectIdAndSeverity(projectId, severity, pageable);

        List<IssueResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.<IssueResponse>builder()
                .content(responses)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLastPage(page.isLast())
                .sortBy(sortBy)
                .sortDirection(PaginationUtil.normalizeSortDirection(sortDirection))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<IssueResponse> getIssuesByProjectAndReporterPaginated(
            String projectId, String reportedBy, int pageNumber, int pageSize, String sortBy, String sortDirection) {
        Pageable pageable = PaginationUtil.getPageable(pageNumber, pageSize, sortBy, sortDirection);
        Page<Issue> page = issueRepository.findByProjectIdAndReportedBy(projectId, reportedBy, pageable);

        List<IssueResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.<IssueResponse>builder()
                .content(responses)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLastPage(page.isLast())
                .sortBy(sortBy)
                .sortDirection(PaginationUtil.normalizeSortDirection(sortDirection))
                .build();
    }

    /* ════════════════════════════════════════════════════════════
       UPDATE (PM only)
       ════════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public IssueResponse updateIssue(String issueId, IssueUpdateRequest request) {
        Issue issue = find(issueId);

        if (request.description() != null && !request.description().isBlank()) {
            issue.setDescription(request.description());
        }
        if (request.status() != null) {
            issue.setStatus(request.status());
            if (request.status() == IssueStatus.RESOLVED || request.status() == IssueStatus.CLOSED) {
                issue.setResolvedAt(LocalDateTime.now());
            }
        }
        if (request.assignedTo() != null) {
            issue.setAssignedTo(request.assignedTo());
            if (issue.getStatus() == IssueStatus.OPEN || issue.getStatus() == IssueStatus.ESCALATED) {
                issue.setStatus(IssueStatus.IN_PROGRESS);
            }
        }
        if (request.resolutionNotes() != null) {
            issue.setResolutionNotes(request.resolutionNotes());
        }

        if (request.allocationId() != null && !request.allocationId().isBlank()) {
            issue.setAllocationId(request.allocationId());
        }
        if (request.resourceId() != null && !request.resourceId().isBlank()) {
            issue.setResourceId(request.resourceId());
        }
        if (request.allocationId() != null && !request.allocationId().isBlank()
                && request.resourceId() != null && !request.resourceId().isBlank()) {
            issue.setStatus(IssueStatus.RESOLVED);
            if (issue.getResolvedAt() == null) {
                issue.setResolvedAt(LocalDateTime.now());
            }
        }

        Issue saved = issueRepository.save(issue);

        String updateMsg = String.format("Issue [%s] has been updated. Status: %s.",
                saved.getIssueId(), saved.getStatus());

        // --- Push ISSUE_UPDATED to central — to the original reporter ---
        // No auth header available in update path; the central client accepts null.
        pushCentral(
                "ISSUE_UPDATED",
                updateMsg,
                null,                              // fromUserId — system update (PM via approval)
                "SITE_ENGINEER",
                saved.getReportedBy(),             // toUserId — original reporter
                saved.getIssueId(),
                null);

        return toResponse(saved);
    }

    /* ════════════════════════════════════════════════════════════
       HELPERS
       ════════════════════════════════════════════════════════════ */

    private Issue find(String issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

    private IssueResponse toResponse(Issue i) {
        return new IssueResponse(
                i.getIssueId(),
                i.getProjectId(),
                i.getLogId(),
                i.getDescription(),
                i.getSeverity(),
                i.getReportedBy(),
                i.getReportedAt(),
                i.getStatus(),
                i.getAssignedTo(),
                i.getResolutionNotes(),
                i.getResolvedAt(),
                i.getApprovalId(),
                i.getResourceType(),
                i.getResourceDescription(),
                i.getResourceFromDate(),
                i.getResourceToDate(),
                i.getAllocationId(),
                i.getResourceId()
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private void notifyPMIssueSubmitted(Issue saved, String authorization) {
        try {
            projectManagerClient.notifyPMIssueSubmitted(
                    new ProjectManagerClient.IssueNotificationPayload(
                            saved.getIssueId(),
                            saved.getProjectId(),
                            saved.getLogId(),
                            saved.getReportedBy(),
                            saved.getSeverity().name(),
                            truncate(saved.getDescription(), 200),
                            saved.getApprovalId()
                    ), authorization);
        } catch (Exception e) {
            log.warn("PM Feign notification for issue '{}' could not be sent: {}",
                    saved.getIssueId(), e.getMessage());
        }
    }

    /**
     * Returns the validated project so the caller can extract createdBy
     * for central-notification routing. Returns null only on graceful
     * service-unavailable; throws on hard validation failures.
     */
    private ProjectDto validateProjectForIssue(String projectId, String authorization) {
        ProjectDto project;
        try {
            project = projectManagerClient.getProject(projectId, authorization);
            if (project == null) {
                throw new IllegalStateException(
                        "Unable to reach the Project Manager service to validate project '" + projectId
                                + "'. Please ensure the PM service is running and try again.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    "Project '" + projectId + "' does not exist in the Project Manager system. "
                            + "Make sure the PM has created this project before reporting an issue.");
        } catch (FeignException e) {
            log.warn("Could not reach Project Manager service to validate project '{}': {}", projectId, e.getMessage());
            throw new IllegalStateException(
                    "Unable to reach the Project Manager service to validate project '" + projectId
                            + "'. Please try again in a moment.");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(project.startDate())) {
            throw new IllegalArgumentException(
                    "Project '" + projectId + "' has not started yet (start date: " + project.startDate() + "). "
                            + "Issues can only be reported during the project's active period.");
        }
        if (today.isAfter(project.endDate())) {
            throw new IllegalArgumentException(
                    "Project '" + projectId + "' has ended (end date: " + project.endDate() + "). "
                            + "Issues can only be reported during the project's active period.");
        }
        return project;
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * toUserId is required; if null/blank the push is skipped.
     */
    private void pushCentral(String eventType, String message,
                             String fromUserId,
                             String toRole, String toUserId,
                             String referenceId,
                             String authorization) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(
                    new NotificationCreateRequest(
                            eventType,
                            message,
                            "siteops-service",
                            "SITE_ENGINEER",
                            fromUserId,
                            toRole,
                            toUserId,
                            referenceId,
                            null
                    ),
                    authorization);
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
}