package com.buildsmart.vendor.service.impl;

import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.PmNotificationClient;
import com.buildsmart.vendor.client.PmTaskSubmissionClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.client.dto.PmNotificationDto;
import com.buildsmart.vendor.dto.response.AssignedTaskResponse;
import com.buildsmart.vendor.dto.response.AssignedTaskSyncResult;
import com.buildsmart.vendor.entity.AssignedTask;
import com.buildsmart.vendor.enums.AssignedTaskStatus;
import com.buildsmart.vendor.repository.AssignedTaskRepository;
import com.buildsmart.vendor.security.AuthenticatedUserResolver;
import com.buildsmart.vendor.service.AssignedTaskService;
import com.buildsmart.vendor.util.IdGeneratorUtil;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final NotificationServiceClient notificationServiceClient;

    @Override
    public AssignedTaskSyncResult syncTasksFromPm(HttpServletRequest request) {
        String currentUserId = requireUserId(request);

        List<PmNotificationDto> pmNotifications;
        try {
            pmNotifications = pmNotificationClient.getNotificationsTo(currentUserId);
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

        log.info("Task sync for vendor {}: {} new, {} already existed", currentUserId, newCount, existedCount);
        return new AssignedTaskSyncResult(newCount, existedCount, newTasks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasks(HttpServletRequest request) {
        String currentUserId = requireUserId(request);
        return assignedTaskRepository
                .findByAssignedToOrderBySyncedAtDesc(currentUserId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksByStatus(HttpServletRequest request, AssignedTaskStatus status) {
        String currentUserId = requireUserId(request);
        return assignedTaskRepository
                .findByAssignedToAndStatusOrderBySyncedAtDesc(currentUserId, status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksForProject(HttpServletRequest request, String projectId) {
        String currentUserId = requireUserId(request);
        return assignedTaskRepository
                .findByAssignedToAndProjectId(currentUserId, projectId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Submits a vendor's assigned task to PM for approval.
     * Local status moves to SUBMITTED (clear any prior rejection reason);
     * PM creates an ApprovalRequest visible at GET /api/approvals.
     */
    @Override
    public AssignedTaskResponse submitTask(HttpServletRequest request, String assignedTaskId, String remarks) {
        String currentUserId = requireUserId(request);

        // Accept either the local id (e.g. VAT001) or the pmTaskId (e.g. VN003).
        // The frontend usually shows the pmTaskId, so users tend to pass that one.
        AssignedTask task = assignedTaskRepository.findById(assignedTaskId)
                .or(() -> assignedTaskRepository.findByPmTaskId(assignedTaskId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "AssignedTask not found: " + assignedTaskId));

        if (!currentUserId.equals(task.getAssignedTo())) {
            throw new IllegalStateException("Only the assigned vendor may submit this task.");
        }

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
                    : "Vendor task submitted by " + currentUserId;
            pmTaskSubmissionClient.submitTaskForApproval(
                    saved.getPmTaskId(), java.util.Map.of("description", description));
            log.info("PM submission accepted for vendor task {} (pmTaskId={})",
                    saved.getId(), saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("Could not submit PM task {} for approval: {}",
                    saved.getPmTaskId(), ex.getMessage());
        }

        // Notify PM via central notification-service (fire-and-forget)
        try {
            String submissionDesc = (remarks != null && !remarks.isBlank())
                    ? remarks
                    : "Vendor task submitted by " + currentUserId;
            String msg = "Vendor task [" + saved.getPmTaskId() + "] has been submitted for approval"
                    + " by Vendor " + currentUserId + ". Notes: " + submissionDesc;
            notificationServiceClient.create(new NotificationCreateRequest(
                    "TASK_SUBMITTED",
                    msg,
                    "vendor-contract-management",
                    "VENDOR",
                    null,
                    "PROJECT_MANAGER",
                    null,
                    saved.getPmTaskId(),
                    null
            ));
            log.info("TASK_SUBMITTED notification sent to PM for vendor task {}", saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("notification-service push for vendor task {} submission failed: {}",
                    saved.getPmTaskId(), ex.getMessage());
        }

        return toResponse(saved);
    }

    /**
     * Handles the approval-result callback from PM.
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
        log.info("Vendor AssignedTask {} updated to {} via PM callback (pmTaskId={})",
                saved.getId(), saved.getStatus(), pmTaskId);

        // Notify the vendor of the PM's decision (fire-and-forget)
        try {
            String eventType = approved ? "TASK_COMPLETED" : "TASK_REJECTED";
            String msg = approved
                    ? "Your vendor task [" + pmTaskId + "] has been APPROVED by the Project Manager"
                            + " and is now marked COMPLETED. Well done!"
                    : "Your vendor task [" + pmTaskId + "] was REJECTED by the Project Manager."
                            + " Reason: " + (rejectionReason == null || rejectionReason.isBlank()
                                    ? "(no reason given)" : rejectionReason)
                            + ". Please rework and resubmit.";
            notificationServiceClient.create(new NotificationCreateRequest(
                    eventType,
                    msg,
                    "project-service",
                    "PROJECT_MANAGER",
                    null,
                    "VENDOR",
                    null,
                    pmTaskId,
                    null
            ));
            log.info("{} notification sent to VENDOR for task {}", eventType, pmTaskId);
        } catch (Exception ex) {
            log.warn("notification-service push for vendor task {} approval result failed: {}",
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

    private String requireUserId(HttpServletRequest request) {
        String userId = authenticatedUserResolver.getCurrentUserId(request);
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Could not resolve authenticated vendor userId");
        }
        return userId;
    }
}
