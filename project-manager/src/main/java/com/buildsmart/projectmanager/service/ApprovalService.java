package com.buildsmart.projectmanager.service;

import com.buildsmart.projectmanager.dto.CreateApprovalRequest;
import com.buildsmart.projectmanager.entity.*;
import com.buildsmart.projectmanager.exception.*;
import com.buildsmart.projectmanager.feign.NotificationServiceClient;
import com.buildsmart.projectmanager.feign.TaskApprovalCallbackClients;
import com.buildsmart.projectmanager.feign.TaskApprovalCallbackClients.ApprovalResultPayload;
import com.buildsmart.projectmanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository taskRepository;
    private final NotificationService notificationService;
    private final IdGeneratorService idGeneratorService;
    // Feign client to push notifications to the dedicated notification microservice
    private final NotificationServiceClient notificationServiceClient;
    // Feign callbacks back to originating downstream services (safety/siteops/finance/vendor)
    private final TaskApprovalCallbackClients.SafetyCallbackClient safetyCallback;
    private final TaskApprovalCallbackClients.SiteOpsCallbackClient siteOpsCallback;
    private final TaskApprovalCallbackClients.FinanceCallbackClient financeCallback;
    private final TaskApprovalCallbackClients.VendorCallbackClient vendorCallback;

    @Transactional
    public ApprovalRequest createApprovalRequest(CreateApprovalRequest request) {
        Project project = projectRepository.findByProjectId(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));

        ProjectTask task = taskRepository.findByTaskId(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", request.getTaskId()));

        String approvalId = request.getApprovalId();
        if (approvalId != null && !approvalId.isBlank()) {
            if (approvalRepository.existsByApprovalId(approvalId)) {
                throw new DuplicateApprovalIdException(approvalId);
            }
        } else {
            approvalId = idGeneratorService.generateApprovalId();
        }

        ApprovalRequest approval = ApprovalRequest.builder()
                .approvalId(approvalId)
                .project(project)
                .task(task)
                .approvalType(ApprovalType.valueOf(request.getApprovalType()))
                .description(request.getDescription())
                .amount(request.getAmount())
                .status(ApprovalStatus.PENDING)
                .requestedByName(request.getRequestedBy() != null ? request.getRequestedBy() : "System")
                .requestedByDepartment(request.getRequestedByDepartment() != null ? DepartmentCode.valueOf(request.getRequestedByDepartment()) : DepartmentCode.FINANCE_OFFICER)
                .requestedAt(LocalDateTime.now())
                .build();

        approval = approvalRepository.save(approval);

        task.setStatus(TaskStatus.AWAITING_APPROVAL);
        taskRepository.save(task);

        return approval;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getAllApprovals() {
        return approvalRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingApprovals() {
        return approvalRepository.findByStatus(ApprovalStatus.PENDING);
    }

    /**
     * Returns all approvals for projects owned by the given PROJECT_MANAGER userId.
     * Used to scope GET /approvals when the caller is a PROJECT_MANAGER (not ADMIN).
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> getApprovalsByProjectOwner(String createdBy) {
        return approvalRepository.findByProjectCreatedBy(createdBy);
    }

    /**
     * Returns pending approvals for projects owned by the given PROJECT_MANAGER userId.
     * Used to scope GET /approvals/pending when the caller is a PROJECT_MANAGER (not ADMIN).
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingApprovalsByProjectOwner(String createdBy) {
        return approvalRepository.findByProjectCreatedByAndStatus(createdBy, ApprovalStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getApprovalsByProject(String projectId) {
        return approvalRepository.findByProjectProjectId(projectId);
    }

    @Transactional
    public ApprovalRequest approveRequest(String approvalId) {
        ApprovalRequest approval = approvalRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval", approvalId));

        approval.setStatus(ApprovalStatus.ACCEPTED);
        approval.setApprovedAt(LocalDateTime.now());
        approval = approvalRepository.save(approval);

        // BUG FIX: was IN_PROGRESS — must be COMPLETED when task is approved
        ProjectTask task = approval.getTask();
        task.setStatus(TaskStatus.COMPLETED);
        task.setActualEnd(java.time.LocalDate.now());
        taskRepository.save(task);

        // Store an internal PM notification for the assignee
        notificationService.notifyTaskApproved(task, approvalId);

        // Also push to the dedicated notification microservice (fire-and-forget)
        // Send APPROVAL_GRANTED event with metadata to user's service
        try {
            String approver = getCurrentUser();
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("taskId", task.getTaskId());
            metadata.put("status", "COMPLETED");
            metadata.put("approvalComments", "");
            metadata.put("approvedAt", java.time.LocalDate.now().toString());
            metadata.put("approvedBy", approver);

            pushExternalNotificationWithMetadata(
                    "APPROVAL_GRANTED",
                    String.format("Task '%s' has been APPROVED and marked COMPLETED. Well done!", task.getTaskId()),
                    approver,                                                                            // fromUserId — the PM approver
                    task.getAssignedDepartment() != null ? task.getAssignedDepartment().name() : null,
                    task.getAssignedTo(),                                                                // toUserId — the assignee
                    task.getTaskId(),
                    metadata
            );
        } catch (Exception ex) {
            log.warn("Could not push APPROVAL_GRANTED notification to notification-service for task '{}': {}",
                    task.getTaskId(), ex.getMessage());
        }

        // Notify the originating downstream service so it can mark its local task COMPLETED
        notifyOriginatingService(task, "APPROVED", null, approvalId);

        return approval;
    }

    @Transactional
    public ApprovalRequest rejectRequest(String approvalId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new MissingRejectionReasonException();
        }

        ApprovalRequest approval = approvalRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval", approvalId));

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setRejectionReason(rejectionReason);
        approval = approvalRepository.save(approval);

        // Task reverts to PENDING so the assignee can rework and resubmit
        ProjectTask task = approval.getTask();
        task.setStatus(TaskStatus.REJECTED);
        taskRepository.save(task);

        // Store an internal PM notification for the assignee (includes rejection reason)
        notificationService.notifyTaskRejected(task, approvalId, rejectionReason);

        // Also push to the dedicated notification microservice (fire-and-forget)
        // Send APPROVAL_REJECTED event with metadata to user's service
        try {
            String rejector = getCurrentUser();
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("taskId", task.getTaskId());
            metadata.put("status", "REJECTED");
            metadata.put("rejectionReason", rejectionReason);
            metadata.put("rejectedAt", java.time.LocalDate.now().toString());
            metadata.put("rejectedBy", rejector);

            pushExternalNotificationWithMetadata(
                    "APPROVAL_REJECTED",
                    String.format(
                            "Task '%s' was REJECTED. Reason: %s. Please rework and resubmit.",
                            task.getTaskId(), rejectionReason),
                    rejector,                                                                            // fromUserId — the PM rejector
                    task.getAssignedDepartment() != null ? task.getAssignedDepartment().name() : null,
                    task.getAssignedTo(),                                                                // toUserId — the assignee
                    task.getTaskId(),
                    metadata
            );
        } catch (Exception ex) {
            log.warn("Could not push APPROVAL_REJECTED notification to notification-service for task '{}': {}",
                    task.getTaskId(), ex.getMessage());
        }

        // Notify the originating downstream service so it can mark its local task NOT_COMPLETED + reason
        notifyOriginatingService(task, "REJECTED", rejectionReason, approvalId);

        return approval;
    }

    /**
     * Routes the approval result back to the originating downstream service via Feign,
     * picked by the task's assignedDepartment. Fire-and-forget: failures are logged
     * but do not roll back the PM-side approval transaction.
     *
     * The {@code approvalId} is forwarded so the vendor service can reconcile
     * Invoice/Document rows that are tracked by approvalId. Other downstream
     * services that decode the payload as Map ignore the extra field.
     */
    private void notifyOriginatingService(ProjectTask task, String decision,
                                          String rejectionReason, String approvalId) {
        if (task == null || task.getAssignedDepartment() == null) return;
        ApprovalResultPayload payload = new ApprovalResultPayload(
                task.getTaskId(), decision, rejectionReason, approvalId);
        try {
            switch (task.getAssignedDepartment()) {
                case SAFETY_OFFICER  -> safetyCallback.notifyApprovalResult(payload);
                case SITE_ENGINEER   -> siteOpsCallback.notifyApprovalResult(payload);
                case FINANCE_OFFICER -> financeCallback.notifyApprovalResult(payload);
                case VENDOR          -> vendorCallback.notifyApprovalResult(payload);
                default -> log.debug("No downstream callback configured for department {}",
                        task.getAssignedDepartment());
            }
            log.info("Approval-result callback sent to {} for task {} (decision={})",
                    task.getAssignedDepartment(), task.getTaskId(), decision);
        } catch (Exception ex) {
            log.warn("Approval-result callback to {} for task {} failed: {}",
                    task.getAssignedDepartment(), task.getTaskId(), ex.getMessage());
        }
    }

    /**
     * Fire-and-forget call to the external notification microservice.
     * Failures are logged but do NOT roll back the approval transaction.
     *
     * fromUserId/toUserId are REQUIRED for per-user routing. If toUserId is
     * null/blank the central service rejects with @NotBlank — callers should
     * resolve a specific recipient (e.g. the task's assignedTo) before invoking.
     */
    private void pushExternalNotification(String eventType, String message,
                                          String fromUserId,
                                          String toRole, String toUserId,
                                          String referenceId) {
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                    eventType, message, "project-service", "PROJECT_MANAGER",
                    fromUserId, toRole, toUserId, referenceId, null));
        } catch (Exception ex) {
            // Non-blocking — notification failure must not fail the core operation
            log.warn("Could not push notification to notification-service for event '{}', ref '{}': {}",
                    eventType, referenceId, ex.getMessage());
        }
    }

    /**
     * Fire-and-forget call to the external notification microservice with metadata.
     * Metadata is serialized as a nested JSON object in the payload field.
     * Failures are logged but do NOT roll back the approval transaction.
     *
     * fromUserId/toUserId are REQUIRED for per-user routing. If toUserId is
     * null/blank the central service rejects with @NotBlank — callers should
     * resolve a specific recipient (e.g. the task's assignedTo) before invoking.
     */
    private void pushExternalNotificationWithMetadata(
            String eventType,
            String message,
            String fromUserId,
            String toRole,
            String toUserId,
            String referenceId,
            java.util.Map<String, String> metadata) {
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            // Serialize metadata as JSON string for the payload field
            String metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
            notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                    eventType,
                    message,
                    "project-service",
                    "PROJECT_MANAGER",
                    fromUserId,
                    toRole,
                    toUserId,
                    referenceId,
                    metadataJson  // metadata passed in payload field
            ));
        } catch (Exception ex) {
            // Non-blocking — notification failure must not fail the core operation
            log.warn("Could not push notification with metadata to notification-service for event '{}', ref '{}': {}",
                    eventType, referenceId, ex.getMessage());
        }
    }

    /**
     * Resolves the currently authenticated user's userId from the security context.
     * Returns "unknown" when no auth context is present (e.g. service-to-service
     * call) — same fallback pattern used by ProjectService.resolveCreatedBy().
     */
    private String getCurrentUser() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "unknown";
        }
        return auth.getName();
    }

    @Transactional(readOnly = true)
    public ApprovalStats getApprovalStats() {
        return new ApprovalStats(
                approvalRepository.countByStatus(ApprovalStatus.PENDING),
                approvalRepository.countByStatus(ApprovalStatus.ACCEPTED),
                approvalRepository.countByStatus(ApprovalStatus.REJECTED),
                approvalRepository.count()
        );
    }

    public record ApprovalStats(long pending, long accepted, long rejected, long total) {}
}