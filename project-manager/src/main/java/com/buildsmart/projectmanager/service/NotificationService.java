package com.buildsmart.projectmanager.service;

import com.buildsmart.projectmanager.dto.InternalNotificationRequest;
import com.buildsmart.projectmanager.dto.NotificationResponse;
import com.buildsmart.projectmanager.entity.*;
import com.buildsmart.projectmanager.exception.ResourceNotFoundException;
import com.buildsmart.projectmanager.feign.NotificationServiceClient;
import com.buildsmart.projectmanager.feign.NotificationServiceClient.NotificationPayload;
import com.buildsmart.projectmanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final IdGeneratorService idGeneratorService;
    private final ProjectRepository projectRepository;
    /**
     * Central notification-service client — every event written into PM's
     * local table is also pushed to the platform-wide notification-service
     * targeted at the specific recipient userId.
     */
    private final NotificationServiceClient notificationServiceClient;

    // ── Vendor notification helpers ───────────────────────────────────────────

    @Transactional
    public void notifyVendor(String projectId, String pmUserId,
                             String fromUserId, String notifType,
                             String title, String message) {
        Project project = projectRepository.findByProjectId(projectId).orElse(null);
        if (project == null) return;
        String resolvedPm = (pmUserId != null && !pmUserId.isBlank()) ? pmUserId : project.getCreatedBy();
        if (resolvedPm == null || resolvedPm.isBlank() || "unknown".equals(resolvedPm)) return;

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(resolvedPm)
                .notificationFrom(fromUserId)
                .notificationTo(resolvedPm)
                .project(project)
                .type(NotificationType.PROJECT_UPDATE)
                .title(title)
                .message(message)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // --- Push to central ---
        // Recipient: the PM. fromUserId: the vendor user that triggered this.
        pushCentral(notifType != null ? notifType : "PROJECT_UPDATE",
                title + " — " + message,
                fromUserId,
                "PROJECT_MANAGER", resolvedPm,
                projectId);
    }

    @Transactional
    public void notifySiteLogSubmitted(String logId, String projectId, String submittedBy,
                                       String approvalId, String activitiesSummary) {
        Project project = projectRepository.findByProjectId(projectId).orElse(null);
        if (project == null) return;

        String pmUserId = project.getCreatedBy();
        if (pmUserId == null || pmUserId.isBlank() || "unknown".equals(pmUserId)) return;

        String activitiesNote = (activitiesSummary != null && !activitiesSummary.isBlank())
                ? " Activities: " + activitiesSummary : "";
        String approvalNote = (approvalId != null && !approvalId.isBlank())
                ? " Approval ID: " + approvalId + "." : "";

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(pmUserId)
                .notificationFrom(submittedBy)
                .notificationTo(pmUserId)
                .project(project)
                .type(NotificationType.APPROVAL_REQUIRED)
                .title("Site Log Submitted for Review: " + logId)
                .message("Site Engineer " + submittedBy + " submitted daily site log ["
                        + logId + "] for project " + projectId
                        + " and it is awaiting your review." + activitiesNote + approvalNote)
                .isRead(false)
                .build();

        notificationRepository.save(notification);

        // --- Push to central — to PM by userId ---
        pushCentral("SITE_LOG_SUBMITTED",
                "Site log " + logId + " submitted for project " + projectId + " by " + submittedBy + ".",
                submittedBy,
                "PROJECT_MANAGER", pmUserId,
                logId);
    }

    @Transactional
    public void notifyIssueSubmitted(String issueId, String projectId, String reportedBy,
                                     String severity, String description) {
        Project project = projectRepository.findByProjectId(projectId).orElse(null);
        if (project == null) return;

        String pmUserId = project.getCreatedBy();
        if (pmUserId == null || pmUserId.isBlank() || "unknown".equals(pmUserId)) return;

        String severityNote = (severity != null && !severity.isBlank())
                ? " [Severity: " + severity + "]" : "";
        String descNote = (description != null && !description.isBlank())
                ? " Description: " + description : "";

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(pmUserId)
                .notificationFrom(reportedBy)
                .notificationTo(pmUserId)
                .project(project)
                .type(NotificationType.APPROVAL_REQUIRED)
                .title("Issue Reported: " + issueId + severityNote)
                .message("Site Engineer " + reportedBy + " reported an issue ["
                        + issueId + "] on project " + projectId + "." + severityNote + descNote)
                .isRead(false)
                .build();

        notificationRepository.save(notification);

        // --- Push to central — to PM by userId ---
        pushCentral("ISSUE_REPORTED",
                "Issue " + issueId + " reported on project " + projectId
                        + " by " + reportedBy + severityNote + descNote,
                reportedBy,
                "PROJECT_MANAGER", pmUserId,
                issueId);
    }

    @Transactional
    public void notifyTaskSubmitted(ProjectTask task, String description) {
        String pmUserId = task.getAssignedBy();
        if (pmUserId == null || pmUserId.isBlank() || "unknown".equals(pmUserId)) return;

        String dept = task.getAssignedDepartment() != null
                ? task.getAssignedDepartment().name() : "UNKNOWN";
        String note = (description != null && !description.isBlank())
                ? " Notes: " + description : "";

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(pmUserId)
                .notificationFrom(task.getAssignedTo())
                .notificationTo(pmUserId)
                .project(task.getProject())
                .type(NotificationType.APPROVAL_REQUIRED)
                .title("Approval Required: " + task.getTaskId())
                .message("Task [" + task.getTaskId() + "] has been submitted for your approval"
                        + " by " + task.getAssignedTo() + " (" + dept + ")." + note)
                .isRead(false)
                .relatedTask(task)
                .build();

        notificationRepository.save(notification);

        // --- Push to central — to the PM ---
        pushCentral("TASK_SUBMITTED",
                "Task " + task.getTaskId() + " submitted for approval by "
                        + task.getAssignedTo() + " (" + dept + ")." + note,
                task.getAssignedTo(),
                "PROJECT_MANAGER", pmUserId,
                task.getTaskId());
    }

    @Transactional
    public void notifyTaskApproved(ProjectTask task, String approvalId) {
        if (task.getAssignedTo() == null || task.getAssignedTo().isBlank()) return;

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(task.getAssignedTo())
                .notificationFrom("Project Manager")
                .notificationTo(task.getAssignedTo())
                .project(task.getProject())
                .type(NotificationType.APPROVAL_ACCEPTED)
                .title("Task Approved: " + task.getTaskId())
                .message("Your task (" + task.getTaskId() + ") has been APPROVED and is now COMPLETED.")
                .isRead(false)
                .relatedTask(task)
                .build();

        notificationRepository.save(notification);

        // NOTE: ApprovalService.approve() already pushes APPROVAL_GRANTED to
        // central, so we deliberately do NOT push again here.
    }

    @Transactional
    public void notifyTaskRejected(ProjectTask task, String approvalId, String rejectionReason) {
        if (task.getAssignedTo() == null || task.getAssignedTo().isBlank()) return;

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(task.getAssignedTo())
                .notificationFrom("Project Manager")
                .notificationTo(task.getAssignedTo())
                .project(task.getProject())
                .type(NotificationType.APPROVAL_REJECTED)
                .title("Task Rejected: " + task.getTaskId())
                .message("Your task (" + task.getTaskId() + ") was REJECTED. Reason: "
                        + rejectionReason + ". Please rework and resubmit.")
                .isRead(false)
                .relatedTask(task)
                .build();

        notificationRepository.save(notification);

        // NOTE: ApprovalService.reject() already pushes APPROVAL_REJECTED to
        // central; no duplicate here.
    }

    @Transactional
    public void notifyTaskAssignment(ProjectTask task) {
        if (task.getAssignedTo() == null || task.getAssignedTo().isBlank()) {
            return;
        }

        Notification notification = Notification.builder()
                .notificationId(idGeneratorService.generateNotificationId())
                .userId(task.getAssignedTo())
                .notificationFrom(task.getAssignedBy())
                .notificationTo(task.getAssignedTo())
                .project(task.getProject())
                .type(NotificationType.TASK_ASSIGNED)
                .title("New Task Assigned: " + task.getTaskId())
                .message("You have been assigned a new task (" + task.getTaskId() + "): "
                        + task.getDescription() + ". Planned: "
                        + task.getPlannedStart() + " to " + task.getPlannedEnd())
                .isRead(false)
                .relatedTask(task)
                .build();

        notificationRepository.save(notification);

        // NOTE: ProjectService.createTask() already pushes TASK_ASSIGNED to
        // central with toUserId=assignee. No duplicate here.
    }

    @Transactional
    public NotificationResponse createInternalNotification(InternalNotificationRequest req) {
        String notifId = (req.getNotificationId() != null && !req.getNotificationId().isBlank())
                ? req.getNotificationId()
                : idGeneratorService.generateNotificationId();

        Project project = projectRepository.findByProjectId(req.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", req.getProjectId()));

        Notification notification = Notification.builder()
                .notificationId(notifId)
                .userId(req.getNotificationTo())
                .notificationFrom(req.getNotificationFrom())
                .notificationTo(req.getNotificationTo())
                .project(project)
                .type(NotificationType.PROJECT_UPDATE)
                .title(req.getTitle())
                .message(req.getMessage())
                .isRead(req.getIsRead() != null ? req.getIsRead() : false)
                .build();

        Notification saved = notificationRepository.save(notification);

        // --- Push to central — generic relay; toUserId = notificationTo ---
        if (req.getNotificationTo() != null && !req.getNotificationTo().isBlank()) {
            pushCentral("GENERIC",
                    (req.getTitle() != null ? req.getTitle() + " — " : "") + req.getMessage(),
                    req.getNotificationFrom(),
                    "PROJECT_MANAGER", req.getNotificationTo(),
                    notifId);
        }

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAllNotifications() {
        return notificationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(String notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        notification.setIsRead(true);
        notification = notificationRepository.save(notification);
        return mapToResponse(notification);
    }

    @Transactional
    public int markAllAsRead(String userId) {
        return notificationRepository.markAllAsReadForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForCurrentUser(String userId) {
        return notificationRepository.findByNotificationToOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByFrom(String notificationFrom) {
        return notificationRepository.findByNotificationFromOrderByCreatedAtDesc(notificationFrom)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByTo(String notificationTo) {
        return notificationRepository.findByNotificationToOrderByCreatedAtDesc(notificationTo)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .projectId(notification.getProject().getProjectId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .notificationFrom(notification.getNotificationFrom())
                .notificationTo(notification.getNotificationTo())
                .relatedTaskId(notification.getRelatedTask() != null
                        ? notification.getRelatedTask().getTaskId() : null)
                .relatedApprovalId(notification.getRelatedApproval() != null
                        ? notification.getRelatedApproval().getApprovalId() : null)
                .relatedMilestoneId(notification.getRelatedMilestone() != null
                        ? notification.getRelatedMilestone().getMilestoneId() : null)
                .build();
    }

    /**
     * Fire-and-forget push to the central notification-service.
     * toUserId is required; skipped silently if missing.
     */
    private void pushCentral(String eventType, String message,
                             String fromUserId,
                             String toRole, String toUserId,
                             String referenceId) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(new NotificationPayload(
                    eventType,
                    message,
                    "project-service",
                    "PROJECT_MANAGER",
                    fromUserId,
                    toRole,
                    toUserId,
                    referenceId,
                    null));
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
}