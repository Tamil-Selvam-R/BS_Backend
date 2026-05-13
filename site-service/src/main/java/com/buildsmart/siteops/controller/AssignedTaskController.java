package com.buildsmart.siteops.controller;

import com.buildsmart.siteops.client.NotificationServiceClient;
import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import com.buildsmart.siteops.dto.AssignedTaskResponse;
import com.buildsmart.siteops.dto.AssignedTaskSyncResult;
import com.buildsmart.siteops.entity.SiteLog;
import com.buildsmart.siteops.enums.AssignedTaskStatus;
import com.buildsmart.siteops.enums.SiteLogReviewStatus;
import com.buildsmart.siteops.repository.SiteLogRepository;
import com.buildsmart.siteops.service.AssignedTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Site Engineer-facing tasks controller.
 *
 * Notification policy: the legacy local SiteOps NotificationService has been
 * removed. The approval-result callback now pushes TASK_COMPLETED /
 * TASK_REJECTED and SITE_LOG_APPROVED / SITE_LOG_REJECTED to the central
 * notification-service so the SE sees the outcome from the unified bell icon.
 */
@RestController
@RequestMapping("/api/siteops/tasks")
@RequiredArgsConstructor
@Slf4j
public class AssignedTaskController {

    private final AssignedTaskService assignedTaskService;
    /**
     * Used by the approval-result callback to also flip a SiteLog row when
     * the approvalId in the payload matches a daily site-log submission.
     */
    private final SiteLogRepository siteLogRepository;

    /**
     * Central notification-service client. Replaces the deleted local
     * SiteOps NotificationService — every approval-result event is now routed
     * to the specific Site Engineer by toUserId.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','ADMIN')")
    public ResponseEntity<AssignedTaskSyncResult> sync(
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(assignedTaskService.syncTasksFromPm(authorization));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasks(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) AssignedTaskStatus status) {
        if (status != null) {
            return ResponseEntity.ok(assignedTaskService.getMyTasksByStatus(authorization, status));
        }
        return ResponseEntity.ok(assignedTaskService.getMyTasks(authorization));
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasksForProject(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String projectId) {
        return ResponseEntity.ok(assignedTaskService.getMyTasksForProject(authorization, projectId));
    }

    /**
     * Submit a site task to PM for approval. Local status moves to SUBMITTED;
     * PM's approve/reject decision arrives via /internal/approval-result.
     * Body MUST contain a non-blank 'description' field explaining the work done.
     */
    @PostMapping("/{assignedTaskId}/submit")
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','ADMIN')")
    public ResponseEntity<AssignedTaskResponse> submitTask(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String assignedTaskId,
            @RequestBody java.util.Map<String, String> body) {
        if (body == null) {
            throw new IllegalArgumentException(
                    "Request body is required. Provide a 'description' explaining the work done.");
        }
        String description = body.getOrDefault("description", body.get("remarks"));
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "'description' is required and must explain the work done before submission.");
        }
        return ResponseEntity.ok(assignedTaskService.submitTask(authorization, assignedTaskId, description));
    }

    /**
     * Internal callback invoked by PM (via Feign) after approve/reject.
     * Body: { "pmTaskId": "TASK001", "decision": "APPROVED|REJECTED",
     *         "rejectionReason": "...", "approvalId": "APRSE001" }
     *
     * Behaviour:
     *  - Always updates the AssignedTask row.
     *  - Pushes TASK_COMPLETED / TASK_REJECTED to central — to the SE who owns the task.
     *  - If approvalId matches a SiteLog row, flips it to APPROVED/REJECTED and
     *    pushes SITE_LOG_APPROVED / SITE_LOG_REJECTED to the SE who submitted it.
     *
     * Failures on the SiteLog reconciliation are logged but do not fail the
     * callback, so the AssignedTask part still succeeds.
     *
     * Open in SecurityConfig — service-to-service only.
     */
    @PatchMapping("/internal/approval-result")
    public ResponseEntity<AssignedTaskResponse> approvalResult(
            @RequestBody java.util.Map<String, String> payload) {
        String pmTaskId = payload.get("pmTaskId");
        String decision = payload.get("decision");
        String rejectionReason = payload.get("rejectionReason");
        String approvalId = payload.get("approvalId");

        if (pmTaskId == null || pmTaskId.isBlank() || decision == null || decision.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 1) Always update the local AssignedTask row.
        AssignedTaskResponse taskResponse =
                assignedTaskService.handleApprovalResult(pmTaskId, decision, rejectionReason);

        // 1.5) Push the outcome to the central notification-service so the
        //      Site Engineer sees it from the unified bell icon. toUserId is
        //      the SE (the task's assignedTo).
        try {
            boolean approved = "APPROVED".equalsIgnoreCase(decision);
            String eventType = approved ? "TASK_COMPLETED" : "TASK_REJECTED";
            String notifMsg = approved
                    ? "Your site task [" + pmTaskId + "] has been APPROVED by the Project Manager"
                    + " and is now marked COMPLETED. Well done!"
                    : "Your site task [" + pmTaskId + "] was REJECTED by the Project Manager."
                    + " Reason: " + (rejectionReason == null || rejectionReason.isBlank()
                    ? "(no reason given)" : rejectionReason)
                    + ". Please rework and resubmit.";
            pushCentral(
                    eventType,
                    notifMsg,
                    null,                                          // fromUserId — PM (anonymous in callback)
                    "SITE_ENGINEER",
                    taskResponse.assignedTo(),                     // toUserId — the SE
                    pmTaskId);
        } catch (Exception ex) {
            log.warn("Central task notification push failed for pmTaskId={}: {}", pmTaskId, ex.getMessage());
        }

        // 2) Reconcile a SiteLog row if the approvalId matches one.
        // Sites logs are tracked by approvalId, not by pmTaskId, so we do this
        // independently and tolerantly — a missing match just means this
        // approval was for an AssignedTask only.
        if (approvalId != null && !approvalId.isBlank()) {
            try {
                reconcileSiteLog(approvalId, decision, rejectionReason);
            } catch (Exception ex) {
                log.warn("SiteLog reconciliation failed for approvalId={}: {}",
                        approvalId, ex.getMessage());
            }
        }

        return ResponseEntity.ok(taskResponse);
    }

    /**
     * Updates the SiteLog identified by {@code approvalId} (if any) to APPROVED
     * or REJECTED, persists reviewer info, and pushes a SITE_LOG_APPROVED /
     * SITE_LOG_REJECTED event to the central notification-service for the
     * original site engineer.
     */
    private void reconcileSiteLog(String approvalId, String decision, String rejectionReason) {
        SiteLog siteLog = siteLogRepository.findByApprovalId(approvalId).orElse(null);
        if (siteLog == null) {
            log.debug("No SiteLog matched approvalId={} — skipping reconciliation.", approvalId);
            return;
        }

        boolean approved = "APPROVED".equalsIgnoreCase(decision);
        siteLog.setReviewStatus(approved
                ? SiteLogReviewStatus.APPROVED
                : SiteLogReviewStatus.REJECTED);
        siteLog.setReviewedBy("Project Manager");
        siteLog.setReviewedAt(LocalDateTime.now());
        siteLog.setReviewerComments(approved ? null : rejectionReason);
        SiteLog saved = siteLogRepository.save(siteLog);

        String eventType = approved ? "SITE_LOG_APPROVED" : "SITE_LOG_REJECTED";
        String message = approved
                ? "Your daily site log [" + saved.getLogId() + "] has been APPROVED by the Project Manager."
                : "Your daily site log [" + saved.getLogId() + "] was REJECTED by the Project Manager. "
                + "Reason: " + (rejectionReason != null ? rejectionReason : "(no reason provided)")
                + ". Please review the comments and resubmit a corrected log.";

        try {
            pushCentral(
                    eventType,
                    message,
                    null,                                          // fromUserId — PM (anonymous in callback)
                    "SITE_ENGINEER",
                    saved.getSubmittedBy(),                        // toUserId — the SE who submitted
                    saved.getLogId());
        } catch (Exception ex) {
            log.warn("Failed to push SiteLog approval-result notification (logId={}): {}",
                    saved.getLogId(), ex.getMessage());
        }

        log.info("SiteLog {} reconciled to {} via PM callback (approvalId={})",
                saved.getLogId(), saved.getReviewStatus(), approvalId);
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * toUserId is required; if null/blank, the call is skipped.
     * The callback path has no Authorization context, so we pass null —
     * SecurityConfig must permit the internal endpoint.
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
            notificationServiceClient.create(
                    new NotificationCreateRequest(
                            eventType,
                            message,
                            "siteops-service",
                            "PROJECT_MANAGER",
                            fromUserId,
                            toRole,
                            toUserId,
                            referenceId,
                            null
                    ),
                    null);
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
}