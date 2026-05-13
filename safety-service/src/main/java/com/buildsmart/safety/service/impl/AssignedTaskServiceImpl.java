package com.buildsmart.safety.service.impl;

import com.buildsmart.safety.client.NotificationServiceClient;
import com.buildsmart.safety.client.PmNotificationClient;
import com.buildsmart.safety.client.ProjectClient;
import com.buildsmart.safety.client.UserClient;
import com.buildsmart.safety.client.dto.PmNotificationDto;
import com.buildsmart.safety.client.dto.UserDto;
import com.buildsmart.safety.common.util.IdGeneratorUtil;
import com.buildsmart.safety.domain.model.AssignedTask;
import com.buildsmart.safety.domain.model.AssignedTaskStatus;
import com.buildsmart.safety.domain.repository.AssignedTaskRepository;
import com.buildsmart.safety.exception.UnauthorizedOperationException;
import com.buildsmart.safety.security.JwtUtil;
import com.buildsmart.safety.service.AssignedTaskService;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.AssignedTaskResponse;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.SyncResult;
import com.buildsmart.safety.web.mapper.AssignedTaskMapper;
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

/**
 * Safety assigned-task lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local SafetyNotification entity / repository
 * have been removed — officers and PMs read their notifications from the central
 * service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssignedTaskServiceImpl implements AssignedTaskService {

    private final AssignedTaskRepository assignedTaskRepository;
    private final PmNotificationClient pmNotificationClient;
    private final UserClient userClient;
    private final JwtUtil jwtUtil;
    /** FEATURE SET 5 — used to flip the PM-side task to COMPLETED. */
    private final ProjectClient projectClient;
    /** FEATURE SET 6 — fire-and-forget pushes to the dedicated notification service. */
    private final NotificationServiceClient notificationServiceClient;

    @Override
    public SyncResult syncTasksFromPm() {
        String bearerToken = getAuthorizationHeader();
        UserDto officer = resolveCurrentUser(bearerToken);

        if (!"ACTIVE".equals(officer.status())) {
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + officer.status());
        }
        if (!"SAFETY_OFFICER".equals(officer.role()) && !"ADMIN".equals(officer.role())) {
            throw new UnauthorizedOperationException(
                    "Only SAFETY_OFFICER or ADMIN can sync assigned tasks.");
        }

        // Fetch TASK_ASSIGNED notifications from PM service
        List<PmNotificationDto> pmNotifications;
        try {
            pmNotifications = pmNotificationClient.getNotificationsTo(officer.userId(), bearerToken);
        } catch (FeignException e) {
            log.warn("Could not reach project-service to sync tasks: {}", e.getMessage());
            pmNotifications = List.of();
        }

        int newCount = 0;
        int existedCount = 0;
        List<AssignedTaskResponse> newTasks = new ArrayList<>();

        for (PmNotificationDto notif : pmNotifications) {
            // Only process TASK_ASSIGNED notifications
            if (!"TASK_ASSIGNED".equals(notif.type())) continue;
            if (notif.relatedTaskId() == null || notif.relatedTaskId().isBlank()) continue;

            // Skip already-synced notifications
            if (assignedTaskRepository.existsByPmNotificationId(notif.notificationId())) {
                existedCount++;
                continue;
            }
            // Skip duplicate task IDs (edge case: two notifications for the same task)
            if (assignedTaskRepository.existsByPmTaskId(notif.relatedTaskId())) {
                existedCount++;
                continue;
            }

            // Build AssignedTask from notification data
            AssignedTask last = assignedTaskRepository.findTopByOrderByIdDesc();
            String newId = IdGeneratorUtil.nextAssignedTaskId(last == null ? null : last.getId());

            AssignedTask task = AssignedTask.builder()
                    .id(newId)
                    .pmTaskId(notif.relatedTaskId())
                    .pmNotificationId(notif.notificationId())
                    .projectId(notif.projectId() != null ? notif.projectId() : "")
                    .assignedTo(officer.userId())
                    .assignedBy(notif.notificationFrom() != null ? notif.notificationFrom() : "")
                    .description(buildDescription(notif))
                    .status(AssignedTaskStatus.PENDING)
                    .syncedAt(LocalDateTime.now())
                    .build();

            assignedTaskRepository.save(task);
            newCount++;

            // Push a TASK_ASSIGNED echo to the safety officer's central bell so
            // they immediately see the new task without a refresh.
            pushCentral(
                    "TASK_ASSIGNED",
                    "Project Manager has assigned you a task on project "
                            + task.getProjectId() + ". Task ID: " + task.getPmTaskId()
                            + ". Details: " + task.getDescription(),
                    task.getAssignedBy(),                  // fromUserId — the PM
                    "SAFETY_OFFICER",
                    officer.userId(),                      // toUserId — the officer
                    task.getPmTaskId());

            newTasks.add(AssignedTaskMapper.toResponse(task));
        }

        log.info("Task sync for officer {}: {} new, {} already existed", officer.userId(), newCount, existedCount);
        return new SyncResult(newCount, existedCount, newTasks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasks() {
        String bearerToken = getAuthorizationHeader();
        UserDto officer = resolveCurrentUser(bearerToken);
        return assignedTaskRepository
                .findByAssignedToOrderBySyncedAtDesc(officer.userId())
                .stream().map(AssignedTaskMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksByStatus(AssignedTaskStatus status) {
        String bearerToken = getAuthorizationHeader();
        UserDto officer = resolveCurrentUser(bearerToken);
        return assignedTaskRepository
                .findByAssignedToAndStatusOrderBySyncedAtDesc(officer.userId(), status)
                .stream().map(AssignedTaskMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksForProject(String projectId) {
        String bearerToken = getAuthorizationHeader();
        UserDto officer = resolveCurrentUser(bearerToken);
        return assignedTaskRepository
                .findByAssignedToAndProjectId(officer.userId(), projectId)
                .stream().map(AssignedTaskMapper::toResponse).toList();
    }

    /**
     * Submit a safety task to PM for approval.
     *
     * Replaces the previous "auto-complete on submit" behaviour: safety tasks
     * now follow the standard approval cycle. We:
     *  1. Mark the local AssignedTask SUBMITTED (clear any previous rejection reason).
     *  2. Push the submission to PM via /internal/tasks/{taskId}/submit (fire-and-forget).
     *  3. Push TASK_SUBMITTED to the central notification-service (to PM + echo to officer).
     *
     * PM will call back via /api/safety/tasks/internal/approval-result to flip
     * the local status to COMPLETED (approved) or REJECTED with reason.
     */
    @Override
    public AssignedTaskResponse submitTask(String assignedTaskId, String remarks) {
        String bearerToken = getAuthorizationHeader();
        UserDto officer = resolveCurrentUser(bearerToken);

        // Accept either the local id (e.g. SAT001) or the pmTaskId (e.g. SF003).
        AssignedTask task = assignedTaskRepository.findById(assignedTaskId)
                .or(() -> assignedTaskRepository.findByPmTaskId(assignedTaskId))
                .orElseThrow(() -> new com.buildsmart.safety.exception.TaskNotAssignedToOfficerException(
                        assignedTaskId, officer.userId()));

        // Authorisation: only the officer the task was assigned to (or ADMIN) may submit.
        if (!officer.userId().equals(task.getAssignedTo())
                && !"ADMIN".equals(officer.role())) {
            throw new UnauthorizedOperationException(
                    "Only the assigned safety officer may submit this task.");
        }

        // Idempotent: already approved or already pending PM decision → return as-is.
        if (task.getStatus() == AssignedTaskStatus.COMPLETED
                || task.getStatus() == AssignedTaskStatus.SUBMITTED) {
            return AssignedTaskMapper.toResponse(task);
        }

        // 1) Mark SUBMITTED locally and clear any previous rejection reason.
        task.setStatus(AssignedTaskStatus.SUBMITTED);
        task.setRejectionReason(null);
        AssignedTask saved = assignedTaskRepository.save(task);

        // 2) Submit to PM's internal endpoint via Feign (fire-and-forget).
        try {
            String description = (remarks != null && !remarks.isBlank())
                    ? remarks
                    : "Safety task submitted by " + officer.userId();
            projectClient.submitTaskForApproval(
                    saved.getPmTaskId(),
                    java.util.Map.of("description", description),
                    bearerToken);
            log.info("PM submission accepted for safety task {} (pmTaskId={})",
                    saved.getId(), saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("Could not submit PM task {} for approval: {}",
                    saved.getPmTaskId(), ex.getMessage());
        }

        // 3) Push TASK_SUBMITTED to central notification-service.
        // To PM (the recipient) and an echo back to the officer.
        String msg = "Safety task " + saved.getPmTaskId()
                + " has been submitted to PM for approval by " + officer.userId() + ".";
        pushCentral(
                "TASK_SUBMITTED",
                msg,
                officer.userId(),                          // fromUserId — the officer
                "PROJECT_MANAGER",
                saved.getAssignedBy(),                     // toUserId — the PM who assigned
                saved.getPmTaskId());
        pushCentral(
                "TASK_SUBMITTED",
                "Your safety task " + saved.getPmTaskId() + " has been submitted to PM for approval.",
                officer.userId(),                          // fromUserId — self
                "SAFETY_OFFICER",
                officer.userId(),                          // toUserId — self (echo)
                saved.getPmTaskId());

        return AssignedTaskMapper.toResponse(saved);
    }

    /**
     * Internal callback handler — invoked when PM approves or rejects a previously
     * submitted safety task. Updates the local AssignedTask row accordingly.
     *
     * APPROVED → status = COMPLETED, completedAt = now, rejectionReason cleared
     * REJECTED → status = REJECTED, rejectionReason populated
     */
    @Override
    @Transactional
    public AssignedTaskResponse handleApprovalResult(String pmTaskId, String decision, String rejectionReason) {
        AssignedTask task = assignedTaskRepository.findByPmTaskId(pmTaskId)
                .orElseThrow(() -> new com.buildsmart.safety.exception.TaskNotAssignedToOfficerException(
                        pmTaskId, "(unknown)"));

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

        // Push the outcome to the officer's central bell.
        String eventType = approved ? "TASK_COMPLETED" : "TASK_REJECTED";
        String message = approved
                ? "Your safety task " + pmTaskId + " has been APPROVED and marked COMPLETED."
                : "Your safety task " + pmTaskId + " was REJECTED. Reason: "
                + (rejectionReason == null ? "(no reason)" : rejectionReason)
                + ". Please rework and resubmit.";
        pushCentral(
                eventType,
                message,
                saved.getAssignedBy(),                     // fromUserId — the PM
                "SAFETY_OFFICER",
                saved.getAssignedTo(),                     // toUserId — the officer
                pmTaskId);

        log.info("Safety AssignedTask {} updated to {} via PM callback (pmTaskId={})",
                saved.getId(), saved.getStatus(), pmTaskId);
        return AssignedTaskMapper.toResponse(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildDescription(PmNotificationDto notif) {
        // PM message format: "You have been assigned a new task (TASKXXX): <desc>. Planned: <start> to <end>"
        // Use the full PM message as description so no data is lost.
        String base = notif.title() != null ? notif.title() : "";
        if (notif.message() != null && !notif.message().isBlank()) {
            base = notif.message();
        }
        return base.length() > 1000 ? base.substring(0, 1000) : base;
    }

    /**
     * Fire-and-forget push to central notification-service.
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
            notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                    eventType,
                    message,
                    "safety-service",
                    "SAFETY_OFFICER",
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

    private UserDto resolveCurrentUser(String bearerToken) {
        String token = bearerToken.substring(7);
        try {
            UserClient.IamProfileResponse response = userClient.getCurrentUserProfile(bearerToken);
            if (response == null || response.data() == null) {
                return jwtFallback(token);
            }
            UserClient.UserData d = response.data();
            return new UserDto(d.userId(), d.name(), d.email(), d.role(), d.status());
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new UnauthorizedOperationException("Invalid or expired token");
        } catch (FeignException e) {
            log.warn("IAM unreachable — falling back to JWT claims");
            return jwtFallback(token);
        }
    }

    private UserDto jwtFallback(String token) {
        return new UserDto(
                jwtUtil.extractUserId(token),
                jwtUtil.extractName(token),
                jwtUtil.extractEmail(token),
                jwtUtil.extractRoles(token).stream().findFirst().orElse(""),
                "ACTIVE"
        );
    }

    private String getAuthorizationHeader() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("No active HTTP request");
        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header;
        throw new IllegalStateException("Authorization header missing");
    }
}