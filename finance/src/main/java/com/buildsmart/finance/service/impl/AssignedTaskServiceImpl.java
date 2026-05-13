package com.buildsmart.finance.service.impl;

import com.buildsmart.finance.client.NotificationServiceClient;
import com.buildsmart.finance.client.PmNotificationClient;
import com.buildsmart.finance.client.PmTaskSubmissionClient;
import com.buildsmart.finance.client.UserClient;
import com.buildsmart.finance.client.dto.PmNotificationDto;
import com.buildsmart.finance.dto.response.AssignedTaskResponse;
import com.buildsmart.finance.dto.response.AssignedTaskSyncResult;
import com.buildsmart.finance.entity.AssignedTask;
import com.buildsmart.finance.entity.enums.AssignedTaskStatus;
import com.buildsmart.finance.repository.AssignedTaskRepository;
import com.buildsmart.finance.service.AssignedTaskService;
import com.buildsmart.finance.util.IdGenerator;
import com.buildsmart.finance.util.JwtUtil;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserClient userClient;
    private final JwtUtil jwtUtil; // kept for any other callers; userId now comes from IAM profile
    private final NotificationServiceClient notificationServiceClient;

    @Override
    public AssignedTaskSyncResult syncTasksFromPm(String authorizationHeader) {
        // Safety-service pattern: resolve userId via IAM with JWT fallback so a
        // temporary IAM outage never breaks the sync entirely.
        String bearerToken = (authorizationHeader != null && !authorizationHeader.isBlank())
                ? authorizationHeader
                : getAuthorizationHeaderFromRequest();

        String currentUserId = resolveCurrentUserId(bearerToken);
        log.info("Syncing tasks for finance officer: {}", currentUserId);

        List<PmNotificationDto> pmNotifications;
        try {
            pmNotifications = pmNotificationClient.getNotificationsTo(currentUserId, bearerToken);
            log.info("Fetched {} notifications from PM for user {}", pmNotifications.size(), currentUserId);
            if (pmNotifications.isEmpty()) {
                log.warn("PM returned 0 notifications for userId='{}' — verify PM has TASK_ASSIGNED notifications with notificationTo='{}' in its DB",
                        currentUserId, currentUserId);
            }
        } catch (FeignException e) {
            log.warn("Could not reach project-service to sync tasks: {}", e.getMessage());
            pmNotifications = List.of();
        }

        int newCount = 0;
        int existedCount = 0;
        List<AssignedTaskResponse> newTasks = new ArrayList<>();

        for (PmNotificationDto notif : pmNotifications) {
            log.debug("Processing PM notification: id={}, type={}, relatedTaskId={}, to={}",
                    notif.notificationId(), notif.type(), notif.relatedTaskId(), currentUserId);
            if (!"TASK_ASSIGNED".equals(notif.type())) {
                log.debug("Skipping notification {} — type is '{}', expected 'TASK_ASSIGNED'",
                        notif.notificationId(), notif.type());
                continue;
            }
            if (notif.relatedTaskId() == null || notif.relatedTaskId().isBlank()) {
                log.debug("Skipping notification {} — relatedTaskId is blank", notif.notificationId());
                continue;
            }

            if (assignedTaskRepository.existsByPmNotificationId(notif.notificationId())) {
                existedCount++;
                continue;
            }
            if (assignedTaskRepository.existsByPmTaskId(notif.relatedTaskId())) {
                existedCount++;
                continue;
            }

            AssignedTask last = assignedTaskRepository.findTopByOrderByIdDesc();
            String newId = IdGenerator.nextAssignedTaskId(last == null ? null : last.getId());

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

        log.info("Task sync for finance officer {}: {} new, {} already existed", currentUserId, newCount, existedCount);
        return new AssignedTaskSyncResult(newCount, existedCount, newTasks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasks(String authorizationHeader) {
        String currentUserId = resolveCurrentUserId(authorizationHeader);
        log.debug("Fetching all tasks for finance officer: {}", currentUserId);
        return assignedTaskRepository
                .findByAssignedToOrderBySyncedAtDesc(currentUserId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksByStatus(String authorizationHeader, AssignedTaskStatus status) {
        String currentUserId = resolveCurrentUserId(authorizationHeader);
        return assignedTaskRepository
                .findByAssignedToAndStatusOrderBySyncedAtDesc(currentUserId, status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> getMyTasksForProject(String authorizationHeader, String projectId) {
        String currentUserId = resolveCurrentUserId(authorizationHeader);
        return assignedTaskRepository
                .findByAssignedToAndProjectId(currentUserId, projectId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Submits a finance officer's assigned task to PM for approval.
     * Local status moves to SUBMITTED (clear any prior rejection reason);
     * PM creates an ApprovalRequest visible at GET /api/approvals.
     * The PM approve/reject decision arrives via the approval-result callback.
     */
    @Override
    public AssignedTaskResponse submitTask(String authorizationHeader, String assignedTaskId, String remarks) {
        String currentUser = resolveCurrentUserId(authorizationHeader);

        // Accept either the local id (e.g. FAT001) or the pmTaskId (e.g. FN003).
        AssignedTask task = assignedTaskRepository.findById(assignedTaskId)
                .or(() -> assignedTaskRepository.findByPmTaskId(assignedTaskId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "AssignedTask not found: " + assignedTaskId));

        if (!currentUser.equals(task.getAssignedTo())) {
            throw new IllegalStateException("Only the assigned finance officer may submit this task.");
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
                    : "Finance task submitted by " + currentUser;
            pmTaskSubmissionClient.submitTaskForApproval(
                    saved.getPmTaskId(),
                    java.util.Map.of("description", description),
                    authorizationHeader);
            log.info("PM submission accepted for finance task {} (pmTaskId={})",
                    saved.getId(), saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("Could not submit PM task {} for approval: {}",
                    saved.getPmTaskId(), ex.getMessage());
        }

        // Notify PM via central notification-service (fire-and-forget).
        // Recipient: the PM who assigned the task = saved.getAssignedBy().
        // Sender: the Finance Officer submitting = currentUser.
        try {
            String submissionDesc = (remarks != null && !remarks.isBlank())
                    ? remarks
                    : "Finance task submitted by " + currentUser;
            String msg = "Finance task [" + saved.getPmTaskId() + "] has been submitted for approval"
                    + " by Finance Officer " + currentUser + ". Notes: " + submissionDesc;
            pushCentral(
                    "TASK_SUBMITTED",
                    msg,
                    "finance",
                    "FINANCE_OFFICER",
                    currentUser,                          // fromUserId
                    "PROJECT_MANAGER",
                    saved.getAssignedBy(),                // toUserId — the PM
                    saved.getPmTaskId());
            log.info("TASK_SUBMITTED notification sent to PM for finance task {}", saved.getPmTaskId());
        } catch (Exception ex) {
            log.warn("notification-service push for finance task {} submission failed: {}",
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
        log.info("Finance AssignedTask {} updated to {} via PM callback (pmTaskId={})",
                saved.getId(), saved.getStatus(), pmTaskId);

        // Notify the finance officer of the PM's decision (fire-and-forget).
        // Recipient: the Finance Officer who owns the task = saved.getAssignedTo().
        // Sender: the PM who assigned/decided = saved.getAssignedBy().
        try {
            String eventType = approved ? "TASK_COMPLETED" : "TASK_REJECTED";
            String msg = approved
                    ? "Your finance task [" + pmTaskId + "] has been APPROVED by the Project Manager"
                    + " and is now marked COMPLETED. Well done!"
                    : "Your finance task [" + pmTaskId + "] was REJECTED by the Project Manager."
                    + " Reason: " + (rejectionReason == null || rejectionReason.isBlank()
                    ? "(no reason given)" : rejectionReason)
                    + ". Please rework and resubmit.";
            pushCentral(
                    eventType,
                    msg,
                    "project-service",
                    "PROJECT_MANAGER",
                    saved.getAssignedBy(),                // fromUserId — the PM
                    "FINANCE_OFFICER",
                    saved.getAssignedTo(),                // toUserId — the finance officer
                    pmTaskId);
            log.info("{} notification sent to FINANCE_OFFICER for task {}", eventType, pmTaskId);
        } catch (Exception ex) {
            log.warn("notification-service push for finance task {} approval result failed: {}",
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

    private String currentUserId(String authorizationHeader) {
        return resolveCurrentUserId(authorizationHeader);
    }

    /**
     * Resolves the current user's ID in priority order:
     * 1. Spring SecurityContext principal — set by JwtAuthenticationFilter which already
     *    validated the token with IAM and stored userId (e.g. "BSFO001") as the principal.
     *    This is the fastest and most reliable source; avoids a redundant IAM call.
     * 2. IAM /users/profile Feign call — used when SecurityContext is unavailable
     *    (e.g. async threads). 401/403 rethrown immediately; other Feign errors fall through.
     * 3. JWT claims — last resort; extracts "userId" claim, then "sub" (email) claim.
     */
    private String resolveCurrentUserId(String authorizationHeader) {
        // 1. Read from SecurityContext (most reliable — filter already resolved this)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equals(auth.getName())) {
            log.debug("Resolved userId from SecurityContext: {}", auth.getName());
            return auth.getName();
        }

        // 2. SecurityContext unavailable (async context) — call IAM directly
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalStateException("Missing Authorization header and no SecurityContext");
        }
        String rawToken = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        try {
            UserClient.IamProfileResponse profile = userClient.getCurrentUserProfile(authorizationHeader);
            if (profile != null && profile.data() != null
                    && profile.data().userId() != null && !profile.data().userId().isBlank()) {
                log.debug("Resolved userId from IAM (async fallback): {}", profile.data().userId());
                return profile.data().userId();
            }
            log.warn("IAM profile returned no userId — falling back to JWT claims");
            return jwtFallbackUserId(rawToken);
        } catch (FeignException.Unauthorized | FeignException.Forbidden ex) {
            throw new IllegalStateException("Invalid or expired token: " + ex.getMessage(), ex);
        } catch (FeignException ex) {
            log.warn("IAM unreachable during userId resolution — falling back to JWT claims: {}", ex.getMessage());
            return jwtFallbackUserId(rawToken);
        }
    }

    /**
     * Extracts userId from the JWT token directly.
     * Tries the 'userId' claim first; falls back to 'sub' (email) which the IAM JWT always sets.
     */
    private String jwtFallbackUserId(String rawToken) {
        String userId = jwtUtil.extractUserId(rawToken);
        if (userId != null && !userId.isBlank()) return userId;
        String email = jwtUtil.extractEmail(rawToken); // sub claim
        if (email != null && !email.isBlank()) return email;
        throw new IllegalStateException("Cannot resolve userId from JWT token — token may be malformed");
    }

    /**
     * Reads the Authorization header directly from the current HTTP request via
     * RequestContextHolder — same approach as safety-service. This means callers
     * that already have the header can pass it, but the service also works when
     * the header isn't forwarded explicitly.
     */
    private String getAuthorizationHeaderFromRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("No active HTTP request context");
        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header;
        throw new IllegalStateException("Authorization header missing or not Bearer type");
    }

    /**
     * Helper — fire-and-forget push to the dedicated notification service.
     * toUserId is REQUIRED by the central service. If it is null/blank the push
     * is skipped — never propagated as a 400 to the user's transaction.
     */
    private void pushCentral(String eventType,
                             String message,
                             String fromService,
                             String fromRole,
                             String fromUserId,
                             String toRole,
                             String toUserId,
                             String referenceId) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                eventType,
                message,
                fromService,
                fromRole,
                fromUserId,
                toRole,
                toUserId,
                referenceId,
                null
        ));
    }
}