package com.buildsmart.siteops.service.impl;

import com.buildsmart.siteops.client.ProjectManagerClient;
import com.buildsmart.siteops.client.dto.ProjectDto;
import com.buildsmart.siteops.entity.AssignedTask;
import com.buildsmart.siteops.enums.SiteLogReviewStatus;
import com.buildsmart.siteops.exception.DuplicateResourceException;
import com.buildsmart.siteops.exception.ResourceNotFoundException;
import com.buildsmart.siteops.repository.AssignedTaskRepository;
import com.buildsmart.siteops.util.IdGeneratorUtil;
import com.buildsmart.siteops.util.PaginationUtil;
import com.buildsmart.siteops.dto.PaginatedResponse;
import com.buildsmart.siteops.dto.SiteLogPhotoUploadResponse;
import com.buildsmart.siteops.dto.SiteLogRequest;
import com.buildsmart.siteops.dto.SiteLogResponse;
import com.buildsmart.siteops.entity.SiteLog;
import com.buildsmart.siteops.repository.SiteLogRepository;
import com.buildsmart.siteops.service.SiteLogService;
import com.buildsmart.siteops.validator.SiteLogValidator;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Site log lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local SiteOps NotificationService has
 * been removed — engineers and PMs read their notifications from the central
 * service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteLogServiceImpl implements SiteLogService {

    private final SiteLogRepository   siteLogRepository;
    private final SiteLogValidator    siteLogValidator;
    private final ProjectManagerClient projectManagerClient;
    /**
     * Used by submitSiteLog() to look up an existing SE task for the project.
     * PM's POST /api/approvals requires a valid taskId, so we attach the
     * approval to any active task already assigned to the SE on that project.
     */
    private final AssignedTaskRepository assignedTaskRepository;

    /**
     * Central notification-service client. Pushed on SiteLog submit so the
     * Project Manager (resolved via project.createdBy) and the submitting SE
     * both see a SITE_LOG_SUBMITTED event in their feed. Optional so existing
     * builds without the bean still load.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.buildsmart.siteops.client.NotificationServiceClient notificationServiceClient;

    /**
     * Used to auto-sync the SE's PM tasks just before submitting a site log.
     * Reasoning: PM's createApprovalRequest requires a valid taskId, and the
     * SE may not have explicitly synced their tasks. Triggering a fresh sync
     * here makes the submit flow self-healing. Optional.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.buildsmart.siteops.service.AssignedTaskService assignedTaskService;

    @Value("${app.upload.dir:uploads/sitelogs/}")
    private String uploadDir;

    /* ============================================================
       PHOTO UPLOAD
       ============================================================ */

    @Override
    @Transactional
    public SiteLogPhotoUploadResponse uploadSiteLogPhoto(String logId, MultipartFile photo, String uploadedBy) {
        if (logId == null || logId.isBlank())
            throw new IllegalArgumentException("logId is required for photo upload.");
        if (uploadedBy == null || uploadedBy.isBlank())
            throw new IllegalArgumentException("Authenticated user (uploadedBy) is required.");

        SiteLog log = find(logId);
        if (!uploadedBy.equals(log.getSubmittedBy()))
            throw new IllegalArgumentException("Only the site engineer who created the log can upload its photo.");
        if (log.getPhotoUrl() != null && !log.getPhotoUrl().isBlank())
            throw new DuplicateResourceException("Photo already attached for site log: " + logId);

        validatePhoto(photo);
        log.setPhotoUrl(savePhoto(photo));
        SiteLog saved = siteLogRepository.save(log);
        return new SiteLogPhotoUploadResponse(
                saved.getLogId(), saved.getProjectId(), saved.getPhotoUrl(), LocalDateTime.now());
    }

    /* ============================================================
       CREATE
       ============================================================ */

    @Override
    @Transactional
    public SiteLogResponse createSiteLog(SiteLogRequest request, String submittedBy, String authorization) {
        if (submittedBy == null || submittedBy.isBlank())
            throw new IllegalArgumentException("Authenticated user (submittedBy) is required.");

        siteLogValidator.validate(request);

        // request.logDate() is already validated to be today by SiteLogValidator.
        LocalDate logDate = request.logDate();

        // ── Validate project exists in PM + check date boundaries ──────────
        ProjectDto project = fetchProjectOrThrow(request.projectId(), authorization);
        validateLogDateWithinProject(logDate, project);

        if (siteLogRepository.existsByProjectIdAndLogDate(request.projectId(), logDate)) {
            throw new DuplicateResourceException(
                    "A site log already exists for project " + request.projectId()
                            + " on " + logDate + ". Only one site log per project per day is allowed.");
        }

        siteLogRepository.findTopByProjectIdOrderByLogDateDesc(request.projectId())
                .ifPresent(prev -> {
                    if (request.progressPercent().compareTo(prev.getProgressPercent()) < 0) {
                        throw new IllegalArgumentException(
                                "Progress percent (" + request.progressPercent()
                                        + "%) cannot be less than the previous log (" + prev.getProgressPercent()
                                        + "%). Construction progress is cumulative.");
                    }
                });

        // ── Generate IDs ─────────────────────────────────────────────────
        String lastLogId = siteLogRepository.findTopByOrderByLogIdDesc()
                .map(SiteLog::getLogId).orElse(null);
        String lastApprovalId = siteLogRepository.findTopByApprovalIdNotNullOrderByApprovalIdDesc()
                .map(SiteLog::getApprovalId).orElse(null);

        SiteLog log = new SiteLog();
        log.setLogId(IdGeneratorUtil.nextSiteLogId(lastLogId));
        log.setProjectId(request.projectId());
        log.setLogDate(logDate);
        log.setActivities(request.activities());
        log.setIssuesSummary(request.issuesSummary());
        log.setProgressPercent(request.progressPercent());
        log.setSubmittedBy(submittedBy);
        log.setSubmittedAt(LocalDateTime.now());
        log.setPhotoUrl(null);
        log.setApprovalId(IdGeneratorUtil.nextApprovalId(lastApprovalId));

        SiteLog saved = siteLogRepository.save(log);

        // ── Notify PM via Feign (fire-and-forget; log on failure) ─────────
        notifyPMSiteLogSubmitted(saved, submittedBy, request.activities(), authorization);

        // ── Push the new cumulative progress % to PM so it can recompute
        //     the project's milestone statuses from the template definitions.
        //     Fire-and-forget — must not roll back the site log save.
        pushMilestoneProgress(saved, authorization);

        return toResponse(saved);
    }

    /**
     * Sends the cumulative progressPercent to PM's
     *   POST /api/projects/{projectId}/milestones/progress
     * endpoint so PM redistributes it across the project's template-defined
     * milestones (NOT_STARTED → IN_PROGRESS → COMPLETED). Fire-and-forget.
     */
    private void pushMilestoneProgress(SiteLog saved, String authorization) {
        try {
            projectManagerClient.updateMilestonesByProgress(
                    saved.getProjectId(),
                    java.util.Map.of("progressPercent", saved.getProgressPercent()),
                    authorization);
            log.info("Milestone progress pushed to PM for project {}: {}%",
                    saved.getProjectId(), saved.getProgressPercent());
        } catch (Exception ex) {
            log.warn("Could not push milestone progress for site log '{}' (project {}): {}",
                    saved.getLogId(), saved.getProjectId(), ex.getMessage());
        }
    }

    /* ============================================================
       READ
       ============================================================ */

    @Override
    @Transactional(readOnly = true)
    public SiteLogResponse getSiteLogById(String logId) {
        return toResponse(find(logId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteLogResponse> getSiteLogsByProject(String projectId) {
        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("projectId is required.");
        return siteLogRepository.findByProjectIdOrderByLogDateDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteLogResponse> getSiteLogsByProjectAndDateRange(
            String projectId, LocalDate from, LocalDate to) {
        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("projectId is required.");
        if (from == null || to == null)
            throw new IllegalArgumentException("Both 'from' and 'to' dates are required.");
        if (to.isBefore(from))
            throw new IllegalArgumentException("'to' date must be on or after 'from' date.");

        List<SiteLogResponse> result = siteLogRepository
                .findByProjectIdAndLogDateBetweenOrderByLogDateDesc(projectId, from, to)
                .stream().map(this::toResponse).toList();

        if (result.isEmpty())
            throw new ResourceNotFoundException(
                    "No site log exists for project " + projectId
                            + " in date range " + from + " to " + to + ".");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public SiteLogResponse getSiteLogByProjectAndDate(String projectId, LocalDate date) {
        return toResponse(
                siteLogRepository.findByProjectIdAndLogDate(projectId, date)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No site log found for project " + projectId + " on " + date)));
    }

    @Override
    @Transactional(readOnly = true)
    public SiteLogResponse getLatestSiteLog(String projectId) {
        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("projectId is required.");
        return toResponse(
                siteLogRepository.findTopByProjectIdOrderByLogDateDesc(projectId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No site logs found for project: " + projectId)));
    }

    /* ============================================================
       PAGINATED
       ============================================================ */

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SiteLogResponse> getSiteLogsByProjectPaginated(
            String projectId, int pageNumber, int pageSize, String sortBy, String sortDirection) {

        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("projectId is required.");

        Pageable pageable = PaginationUtil.getPageable(pageNumber, pageSize, sortBy, sortDirection);
        Page<SiteLog> page = siteLogRepository.findByProjectId(projectId, pageable);
        if (page.isEmpty())
            throw new ResourceNotFoundException("No site logs found for project: " + projectId);

        return PaginatedResponse.<SiteLogResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .pageNumber(page.getNumber()).pageSize(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .isLastPage(page.isLast()).sortBy(sortBy)
                .sortDirection(PaginationUtil.normalizeSortDirection(sortDirection))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SiteLogResponse> getSiteLogsByProjectAndDateRangePaginated(
            String projectId, LocalDate from, LocalDate to, int pageNumber, int pageSize,
            String sortBy, String sortDirection) {

        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("projectId is required.");
        if (from == null || to == null)
            throw new IllegalArgumentException("Both 'from' and 'to' dates are required.");
        if (to.isBefore(from))
            throw new IllegalArgumentException("'to' date must be on or after 'from' date.");

        Pageable pageable = PaginationUtil.getPageable(pageNumber, pageSize, sortBy, sortDirection);
        Page<SiteLog> page = siteLogRepository.findByProjectIdAndLogDateBetween(projectId, from, to, pageable);
        if (page.isEmpty())
            throw new ResourceNotFoundException(
                    "No site logs found for project " + projectId
                            + " in date range " + from + " to " + to + ".");

        return PaginatedResponse.<SiteLogResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .pageNumber(page.getNumber()).pageSize(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .isLastPage(page.isLast()).sortBy(sortBy)
                .sortDirection(PaginationUtil.normalizeSortDirection(sortDirection))
                .build();
    }

    /* ============================================================
       SUBMIT FOR PM APPROVAL
       ============================================================ */

    /**
     * Sends a previously-created daily site log to PM for approval.
     *
     * Steps:
     *  1. Validate the log exists and the caller is the original SE.
     *  2. Allow submission only when reviewStatus is PENDING (first submission)
     *     or REJECTED (resubmission after PM feedback).
     *  3. Reset rejection comments if this is a resubmission.
     *  4. Flip reviewStatus to SUBMITTED and persist.
     *  5. Push a formal ApprovalRequest to PM via Feign.
     *  6. Push SITE_LOG_SUBMITTED to the central notification-service so PM
     *     and the SE both see it.
     */
    @Override
    @Transactional
    public SiteLogResponse submitSiteLog(String logId, String submittedBy, String authorization) {
        if (logId == null || logId.isBlank())
            throw new IllegalArgumentException("logId is required.");
        if (submittedBy == null || submittedBy.isBlank())
            throw new IllegalArgumentException("Authenticated user (submittedBy) is required.");

        SiteLog siteLog = find(logId);

        // Ownership: only the engineer who created the log can submit it.
        if (!submittedBy.equals(siteLog.getSubmittedBy())) {
            throw new IllegalArgumentException(
                    "Only the site engineer who created the log can submit it for approval.");
        }

        SiteLogReviewStatus current = siteLog.getReviewStatus();
        if (current == SiteLogReviewStatus.APPROVED) {
            throw new IllegalStateException(
                    "Site log " + logId + " has already been APPROVED — submission not allowed.");
        }
        if (current == SiteLogReviewStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Site log " + logId + " is already SUBMITTED and awaiting PM review.");
        }
        // PENDING (fresh) or REJECTED (resubmission) are both valid entry points.

        // Make sure an approvalId is present.
        if (siteLog.getApprovalId() == null || siteLog.getApprovalId().isBlank()) {
            String lastApprovalId = siteLogRepository.findTopByApprovalIdNotNullOrderByApprovalIdDesc()
                    .map(SiteLog::getApprovalId).orElse(null);
            siteLog.setApprovalId(IdGeneratorUtil.nextApprovalId(lastApprovalId));
        }

        // Capture for rollback — also clear any previous rejection comments now
        // that the SE is putting the log back up for review.
        SiteLogReviewStatus previousStatus = current;
        String previousComments = siteLog.getReviewerComments();
        siteLog.setReviewerComments(null);
        siteLog.setReviewStatus(SiteLogReviewStatus.SUBMITTED);
        SiteLog saved = siteLogRepository.save(siteLog);

        // ── 1) Auto-sync SE's PM tasks ─────────────────────────────────────
        // PM's POST /api/approvals requires a valid taskId. We pick a task
        // already assigned to this SE on the project; if none is cached locally,
        // first run a fresh sync.
        String taskId = pickAnyPmTaskIdForSeOnProject(submittedBy, saved.getProjectId());
        if (taskId == null && assignedTaskService != null) {
            try {
                log.info("No local SE task found for {}/{} — auto-syncing tasks from PM before submission.",
                        submittedBy, saved.getProjectId());
                assignedTaskService.syncTasksFromPm(authorization);
                taskId = pickAnyPmTaskIdForSeOnProject(submittedBy, saved.getProjectId());
            } catch (Exception syncEx) {
                log.warn("Auto-sync of PM tasks failed for SE {}: {}",
                        submittedBy, syncEx.getMessage());
            }
        }

        // ── 2) Push the formal ApprovalRequest to PM ──────────────────────
        if (taskId == null) {
            saved.setReviewStatus(previousStatus);
            saved.setReviewerComments(previousComments);
            siteLogRepository.save(saved);
            throw new IllegalStateException(
                    "Cannot submit site log " + saved.getLogId() + ": no PM task is currently assigned to "
                            + submittedBy + " on project " + saved.getProjectId() + ". "
                            + "Ask the Project Manager to assign you a task on this project, then retry "
                            + "the submission (siteops will auto-sync your tasks on the next attempt).");
        }
        try {
            ProjectManagerClient.ApprovalCreateRequest payload =
                    new ProjectManagerClient.ApprovalCreateRequest(
                            saved.getProjectId(),
                            taskId,
                            saved.getApprovalId(),
                            "SITE_WORK",
                            "Daily site log " + saved.getLogId() + " submitted for approval by "
                                    + submittedBy + " on " + saved.getLogDate()
                                    + ". Activities: "
                                    + (saved.getActivities() != null
                                    ? truncate(saved.getActivities(), 500)
                                    : "(no activities recorded)"),
                            0.0,
                            submittedBy,
                            "SITE_ENGINEER");
            projectManagerClient.createApprovalRequest(payload, authorization);
            log.info("PM approval request created for site log {} (approvalId={})",
                    saved.getLogId(), saved.getApprovalId());
        } catch (Exception e) {
            // PM unreachable / rejected — roll the local status back.
            saved.setReviewStatus(previousStatus);
            saved.setReviewerComments(previousComments);
            siteLogRepository.save(saved);
            log.error("Failed to submit site log {} to PM: {}", saved.getLogId(), e.getMessage());
            throw new RuntimeException(
                    "Failed to submit site log to Project Manager: " + e.getMessage());
        }

        // ── 3) Resolve PM userId and push SITE_LOG_SUBMITTED to central ───
        // The PM sees the event on their bell icon; the SE gets an echo so
        // they have submission confirmation in the same unified feed.
        String pmUserId = null;
        try {
            ProjectDto project = projectManagerClient.getProject(saved.getProjectId(), authorization);
            pmUserId = (project != null) ? project.createdBy() : null;
        } catch (Exception ex) {
            log.warn("Could not resolve PM userId for project {} (notification routing): {}",
                    saved.getProjectId(), ex.getMessage());
        }
        pushPmNotification(saved, submittedBy, pmUserId, authorization);
        pushSeEchoNotification(saved, submittedBy, authorization);

        return toResponse(saved);
    }

    /**
     * Pushes a SITE_LOG_SUBMITTED event to the central notification-service
     * with the PM as the recipient. Fire-and-forget.
     */
    private void pushPmNotification(SiteLog saved, String submittedBy, String pmUserId, String authorization) {
        if (notificationServiceClient == null) return;
        if (pmUserId == null || pmUserId.isBlank()) {
            log.warn("Skipping PM central notification for site log {}: PM userId not resolved",
                    saved.getLogId());
            return;
        }
        try {
            String message = String.format(
                    "Daily site log [%s] for project %s has been SUBMITTED for approval by %s on %s. " +
                            "Approval ID: %s.",
                    saved.getLogId(),
                    saved.getProjectId(),
                    submittedBy,
                    saved.getLogDate(),
                    saved.getApprovalId());

            notificationServiceClient.create(
                    new com.buildsmart.siteops.client.dto.NotificationCreateRequest(
                            "SITE_LOG_SUBMITTED",
                            message,
                            "siteops-service",
                            "SITE_ENGINEER",
                            submittedBy,                          // fromUserId — the SE
                            "PROJECT_MANAGER",
                            pmUserId,                              // toUserId — the PM
                            saved.getApprovalId(),
                            saved.getLogId()),
                    authorization);
            log.info("PM central notification pushed for site log {} (approvalId={}, toUserId={})",
                    saved.getLogId(), saved.getApprovalId(), pmUserId);
        } catch (Exception ex) {
            log.warn("notification-service push to PM failed for site log {}: {}",
                    saved.getLogId(), ex.getMessage());
        }
    }

    /**
     * Pushes a SITE_LOG_SUBMITTED echo back to the submitting Site Engineer
     * so they see the submission confirmation in their unified feed.
     */
    private void pushSeEchoNotification(SiteLog saved, String submittedBy, String authorization) {
        if (notificationServiceClient == null) return;
        if (submittedBy == null || submittedBy.isBlank()) return;
        try {
            String message = "Site log " + saved.getLogId() + " has been submitted to the Project Manager "
                    + "for approval. Approval ID: " + saved.getApprovalId();
            notificationServiceClient.create(
                    new com.buildsmart.siteops.client.dto.NotificationCreateRequest(
                            "SITE_LOG_SUBMITTED",
                            message,
                            "siteops-service",
                            "SITE_ENGINEER",
                            submittedBy,                          // fromUserId — self
                            "SITE_ENGINEER",
                            submittedBy,                          // toUserId — self (echo)
                            saved.getApprovalId(),
                            saved.getLogId()),
                    authorization);
        } catch (Exception ex) {
            log.warn("Could not write SE echo notification for site log {}: {}",
                    saved.getLogId(), ex.getMessage());
        }
    }

    /** Looks up any AssignedTask already synced for the SE on this project. */
    private String pickAnyPmTaskIdForSeOnProject(String seUserId, String projectId) {
        java.util.List<AssignedTask> tasks =
                assignedTaskRepository.findByAssignedToAndProjectId(seUserId, projectId);
        if (tasks == null || tasks.isEmpty()) return null;
        return tasks.get(0).getPmTaskId();
    }

    /** Truncate helper that keeps log payloads readable. */
    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private SiteLog find(String logId) {
        return siteLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Site log not found: " + logId));
    }

    private SiteLogResponse toResponse(SiteLog s) {
        return new SiteLogResponse(
                s.getLogId(), s.getProjectId(), s.getLogDate(), s.getActivities(),
                s.getIssuesSummary(), s.getProgressPercent(), s.getSubmittedBy(),
                s.getSubmittedAt(), s.getReviewStatus(), s.getReviewedBy(), s.getReviewedAt(),
                s.getReviewerComments(), s.getPhotoUrl(), s.getApprovalId()
        );
    }

    /**
     * Fetch the project from PM service.
     * Uses the caller's Authorization header (Bearer token) forwarded via Feign.
     */
    private ProjectDto fetchProjectOrThrow(String projectId, String authorization) {
        try {
            ProjectDto project = projectManagerClient.getProject(projectId, authorization);
            // Resilience4j fallback returns null when PM is unreachable
            if (project == null) {
                throw new IllegalStateException(
                        "Unable to reach the Project Manager service to validate project '" + projectId
                                + "'. Please ensure the PM service is running and try again.");
            }
            return project;
        } catch (IllegalStateException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    "Project '" + projectId + "' does not exist in the Project Manager system. "
                            + "Make sure the PM has created this project before submitting a site log.");
        } catch (FeignException e) {
            log.warn("Could not reach Project Manager service to validate project '{}': {}", projectId, e.getMessage());
            throw new IllegalStateException(
                    "Unable to reach the Project Manager service to validate project '" + projectId
                            + "'. Please try again in a moment.");
        }
    }

    private void validateLogDateWithinProject(LocalDate logDate, ProjectDto project) {
        // logDate is already validated to be today by SiteLogValidator.
        // Here we check that today falls within the project's active period.
        if (logDate.isBefore(project.startDate())) {
            throw new IllegalArgumentException(
                    "Project '" + project.projectId() + "' has not started yet. "
                            + "It is scheduled to start on " + project.startDate() + ". "
                            + "You can only create site logs from " + project.startDate() + " onwards.");
        }
        if (logDate.isAfter(project.endDate())) {
            throw new IllegalArgumentException(
                    "Project '" + project.projectId() + "' ended on " + project.endDate() + ". "
                            + "Site logs cannot be created after the project end date.");
        }
    }

    private void notifyPMSiteLogSubmitted(SiteLog saved, String submittedBy, String activities, String authorization) {
        try {
            String shortActivities = activities != null && activities.length() > 100
                    ? activities.substring(0, 100) + "..." : activities;
            projectManagerClient.notifyPMSiteLogSubmitted(
                    new ProjectManagerClient.SiteLogNotificationPayload(
                            saved.getLogId(),
                            saved.getProjectId(),
                            submittedBy,
                            saved.getApprovalId(),
                            shortActivities
                    ), authorization);
        } catch (Exception e) {
            log.warn("PM notification for site log '{}' could not be sent: {}", saved.getLogId(), e.getMessage());
            // Intentionally non-blocking — site log is already saved.
        }
    }

    private String savePhoto(MultipartFile photo) {
        try {
            Files.createDirectories(Paths.get(uploadDir));
            String filename = UUID.randomUUID() + "-" + photo.getOriginalFilename();
            Path filePath = Paths.get(uploadDir, filename);
            Files.copy(photo.getInputStream(), filePath);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save photo: " + e.getMessage(), e);
        }
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty())
            throw new IllegalArgumentException("Photo proof is required.");
        String contentType = photo.getContentType();
        String fileName = photo.getOriginalFilename() == null ? ""
                : photo.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean jpegByContentType = contentType != null
                && (contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/jpg"));
        boolean jpegByExtension = fileName.endsWith(".jpeg") || fileName.endsWith(".jpg");
        if (!jpegByContentType && !jpegByExtension)
            throw new IllegalArgumentException("Only JPEG/JPG photo format is allowed.");
    }
}