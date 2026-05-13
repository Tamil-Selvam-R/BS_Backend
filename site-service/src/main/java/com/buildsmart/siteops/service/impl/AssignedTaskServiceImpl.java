package com.buildsmart.siteops.service.impl;

import com.buildsmart.siteops.client.NotificationServiceClient;
import com.buildsmart.siteops.client.PmNotificationClient;
import com.buildsmart.siteops.client.PmTaskSubmissionClient;
import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import com.buildsmart.siteops.client.dto.PmNotificationDto;
import com.buildsmart.siteops.dto.AssignedTaskResponse;
import com.buildsmart.siteops.dto.AssignedTaskSyncResult;
import com.buildsmart.siteops.entity.AssignedTask;
import com.buildsmart.siteops.enums.AssignedTaskStatus;
import com.buildsmart.siteops.repository.AssignedTaskRepository;
import com.buildsmart.siteops.security.JwtUtil;
import com.buildsmart.siteops.service.AssignedTaskService;
import com.buildsmart.siteops.util.IdGeneratorUtil;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssignedTaskServiceImpl implements AssignedTaskService {

    private final AssignedTaskRepository assignedTaskRepository;
    private final PmNotificationClient pmNotificationClient;
    private final PmTaskSubmissionClient pmTaskSubmissionClient;
    private final JwtUtil jwtUtil;
    private final NotificationServiceClient notificationServiceClient;

    @Override
    public AssignedTaskSyncResult syncTasksFromPm(String authorizationHeader) {
        String bearerToken = requireBearer(authorizationHeader);
        String token = bearerToken.substring(7);
        String currentUserId = jwtUtil.extractUserId(token);

        List<PmNotificationDto> pmNotifications;
        try {
            pmNotifications = pmNotificationClient.getNotificationsTo(currentUserId, bearerToken);
        } catch (FeignException e) {
            log.warn("Could not reach project-service to sync tasks: {}", e.getMessage());
            pmNotifications = List.of();
        }

        int newCount = 0;
        int existedCount = 0;
        List<AssignedTaskResponse> newTasks = new ArrayList<>();

        for (PmNotificationDto notif : pmNotifications) {
            if (!"TASK_ASSIGNED".equals(notif.type())) continue;
            if (notif.relatedTaskId() == null || notif.relatedTaskId().isBlank()) continue;

            if (assignedTaskRepository.existsByPmNotificationId(notif.notificationId())) {
                existedCount++;
                continue;
            }
            if (assignedTaskRepository.existsByPmTaskId(notif.relatedTaskId())) {
                existedCount++;
                continue;
            }

            AssignedTask last = assignedTaskRepository.findTopByOrderByIdDesc();
            String newId = IdGeneratorUtil.nextAssignedTaskId(last == null ? null : last.getId());

            AssignedTask task = AssignedTask.builder()
                    .id(newId)
                    .pmTaskId(notif.relatedTaskId())
                    .pmNotificationId(notif.notificationId())
                    .projectId(notif.projectId() != null ? notif.projectId() : "")
                    .assignedTo(currentUserId)
                    .assignedBy(notif.notificationFrom() != null ? notif.notificationFrom() : "")
                    .description(buildDescription(notif))
                    .status(AssignedTaskStatus.PENDING)
                    .syncedAt(LocalDateTime.now())
                    .build();

            assignedTaskRepository.save(task);
            newCount++;

            newTasks.add(toResponse(task));
        }

        log.info("Task sync for site engineer {}: {} new, {} already existed", currentUserId, newCount, existedCount);
        return new AssignedTaskSyncResult(newCount, existedCount, newTasks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasks(String authorizationHeader) {
        String currentUserId = currentUserIdFromHeader(authorizationHeader);
        return assignedTaskRepository
                .findByAssignedToOrderBySyncedAtDesc(currentUserId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksByStatus(String authorizationHeader, AssignedTaskStatus status) {
        String currentUserId = currentUserIdFromHeader(authorizationHeader);
        return assignedTaskRepository
                .findByAssignedToAndStatusOrderBySyncedAtDesc(currentUserId, status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksForProject(String authorizationHeader, String projectId) {
        String currentUserId = currentUserIdFromHeader(authorizationHeader);
        return assignedTaskRepository
                .findByAssignedToAndProjectId(currentUserId, projectId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Submits a site engineer's assigned task to PM for approval.
     * Local status moves to SUBMITTED (any prior rejection reason cleared);
     * PM creates an ApprovalRequest visible at GET /api/approvals.
     * The PM approve/reject decision arrives via the approval-result callback.
     */
    @Override
    public AssignedTaskResponse submitTask(String authorizationHeader, String assignedTaskId, String remarks) {
        String currentUserId = currentUserIdFromHeader(authorizationHeader);

        // Accept either the local id (e.g. SAT001) or the pmTaskId (e.g. SE003).
        AssignedTask task = assignedTaskRepository.findById(assignedTaskId)
                .or(() -> assignedTaskRepository.findByPmTaskId(assignedTaskId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "AssignedTask not found: " + assignedTaskId));

        // Authorisation: only the engineer the task was assigned to may submit.
        if (!currentUserId.equals(task.getAssignedTo())) {
            throw new IllegalStateException("Only the assigned engineer may submit this task.");
        }

        // Idempotent: already approved or already pending PM decision → return as-is.
        if (task.getStatus() == AssignedTaskStatus.COMPLETED
                || task.getStatus() == AssignedTaskStatus.SUBMITTED) {
            return toResponse(task);
        }

        task.setStatus(AssignedTaskStatus.SUBMITTED);
        task.setRejectionReason(null);
        AssignedTask saved = assignedTaskRepository.save(task);

        try {
            String description = (remarks != null && !remarks.isBlank())
                    ? remarks
                    : "Site task submitted by " + currentUserId;
            pmTaskSubmissionClient.submitTaskForApproval(
                    saved.getPmTaskId(),
                    java.util.Map.of("description", description),
                    requireBearer(authorizationHeader));
            log.info("PM submission accepted for site task {} (pmTaskId={})",
                    saved.getId(), saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("Could not submit PM task {} for approval: {}",
                    saved.getPmTaskId(), ex.getMessage());
        }

        // Notify PM via central notification-service (fire-and-forget)
        try {
            String submissionDesc = (remarks != null && !remarks.isBlank())
                    ? remarks
                    : "Site task submitted by " + currentUserId;
            String msg = "Site task [" + saved.getPmTaskId() + "] has been submitted for approval"
                    + " by Site Engineer " + currentUserId + ". Notes: " + submissionDesc;
            notificationServiceClient.create(new NotificationCreateRequest(
                    "TASK_SUBMITTED",
                    msg,
                    "siteops",
                    "SITE_ENGINEER",
                    null,
                    "PROJECT_MANAGER",
                    null,
                    saved.getPmTaskId(),
                    null
            ), requireBearer(authorizationHeader));
            log.info("TASK_SUBMITTED notification sent to PM for site task {}", saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("notification-service push for site task {} submission failed: {}",
                    saved.getPmTaskId(), ex.getMessage());
        }

        return toResponse(saved);
    }

    /**
     * Handles the approval-result callback from PM after the assigned task
     * has been approved or rejected.
     * APPROVED → status = COMPLETED, completedAt = now, rejectionReason cleared
     * REJECTED → status = REJECTED, rejectionReason populated
     */
    @Override
    public AssignedTaskResponse handleApprovalResult(String pmTaskId, String decision, String rejectionReason) {
        AssignedTask task = assignedTaskRepository.findByPmTaskId(pmTaskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "AssignedTask not found for pmTaskId: " + pmTaskId));

        boolean approved = "APPROVED".equalsIgnoreCase(decision);
        if (approved) {
            task.setStatus(AssignedTaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setRejectionReason(null);
        } else {
            task.setStatus(AssignedTaskStatus.REJECTED);
            task.setRejectionReason(rejectionReason);
            task.setCompletedAt(null);
        }
        AssignedTask saved = assignedTaskRepository.save(task);
        log.info("SiteOps AssignedTask {} updated to {} via PM callback (pmTaskId={})",
                saved.getId(), saved.getStatus(), pmTaskId);

        // Notify the site engineer of the PM's decision (fire-and-forget)
        try {
            String eventType = approved ? "TASK_COMPLETED" : "TASK_REJECTED";
            String msg = approved
                    ? "Your site task [" + pmTaskId + "] has been APPROVED by the Project Manager"
                            + " and is now marked COMPLETED. Well done!"
                    : "Your site task [" + pmTaskId + "] was REJECTED by the Project Manager."
                            + " Reason: " + (rejectionReason == null || rejectionReason.isBlank()
                                    ? "(no reason given)" : rejectionReason)
                            + ". Please rework and resubmit.";
            notificationServiceClient.create(new NotificationCreateRequest(
                    eventType,
                    msg,
                    "project-service",
                    "PROJECT_MANAGER",
                    null,
                    "SITE_ENGINEER",
                    null,
                    pmTaskId,
                    null
            ), resolveAuthorizationHeaderOrFallback());
            log.info("{} notification sent to SITE_ENGINEER for task {}", eventType, pmTaskId);
        } catch (Exception ex) {
            log.warn("notification-service push for site task {} approval result failed: {}",
                    pmTaskId, ex.getMessage());
        }

        return toResponse(saved);
    }

    private String buildDescription(PmNotificationDto notif) {
        String base = notif.title() != null ? notif.title() : "";
        if (notif.message() != null && !notif.message().isBlank()) {
            base = notif.message();
        }
        return base.length() > 1000 ? base.substring(0, 1000) : base;
    }

    private AssignedTaskResponse toResponse(AssignedTask task) {
        return new AssignedTaskResponse(
                task.getId(),
                task.getPmTaskId(),
                task.getPmNotificationId(),
                task.getProjectId(),
                task.getAssignedTo(),
                task.getAssignedBy(),
                task.getDescription(),
                task.getStatus(),
                task.getLinkedEntityId(),
                task.getSyncedAt(),
                task.getCompletedAt(),
                task.getRejectionReason()
        );
    }

    private String currentUserIdFromHeader(String authorizationHeader) {
        String bearer = requireBearer(authorizationHeader);
        return jwtUtil.extractUserId(bearer.substring(7));
    }

    private String requireBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("Authorization header missing or malformed");
        }
        return authorizationHeader;
    }

    /**
     * Attempts to read the Authorization header from the current servlet request context.
     * Used by handleApprovalResult() which is called via a PM Feign callback and
     * therefore may not always carry an end-user JWT.
     * Falls back to a service-to-service placeholder so the fire-and-forget
     * notification push to the (permitAll) notification endpoint never blocks.
     */
    private String resolveAuthorizationHeaderOrFallback() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) return header;
            }
        } catch (Exception ignored) {}
        return "Bearer internal-service-call";
    }
}
