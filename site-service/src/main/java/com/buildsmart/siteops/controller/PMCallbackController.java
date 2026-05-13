package com.buildsmart.siteops.controller;

import com.buildsmart.siteops.client.NotificationServiceClient;
import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import com.buildsmart.siteops.enums.SiteLogReviewStatus;
import com.buildsmart.siteops.repository.SiteLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Internal callback endpoint — called by Project Manager service when a site-log
 * or issue approval is approved or rejected.
 *
 * Path: /internal/approval-result
 * Security: /internal/** is permitted without authentication in SiteOps SecurityConfig.
 * This is a service-to-service internal endpoint; not exposed to end users.
 *
 * Notification policy: the legacy local SiteOps NotificationService has been
 * removed. The site engineer is notified via the central notification-service
 * with a SITE_LOG_APPROVED or SITE_LOG_REJECTED event addressed to their userId.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class PMCallbackController {

    private final SiteLogRepository siteLogRepository;

    /**
     * Central notification-service client. Replaces the deleted local
     * SiteOps NotificationService. Optional so unit tests / startup without
     * the bean still work.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    /**
     * PM calls this after approving or rejecting a site log.
     *
     * Request body:
     * {
     *   "approvalId":    "APRSE001",      // the approvalId generated when log was submitted
     *   "decision":      "APPROVED",      // or "REJECTED"
     *   "reviewedBy":    "PMUSER001",     // PM's userId
     *   "comments":      "Looks good."    // optional rejection reason / approval notes
     * }
     */
    @PostMapping("/approval-result")
    public ResponseEntity<Map<String, String>> receiveApprovalResult(
            @RequestBody ApprovalResultRequest payload) {

        log.info("Received approval result from PM: approvalId={}, decision={}",
                payload.approvalId(), payload.decision());

        // Find the SiteLog that owns this approvalId
        var siteLogOpt = siteLogRepository.findByApprovalId(payload.approvalId());
        if (siteLogOpt.isEmpty()) {
            log.warn("No site log found for approvalId '{}' — notification skipped.", payload.approvalId());
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "No site log found for approvalId: " + payload.approvalId()));
        }

        var siteLog = siteLogOpt.get();

        // Update review status on the SiteLog
        boolean approved = "APPROVED".equalsIgnoreCase(payload.decision());
        siteLog.setReviewStatus(approved ? SiteLogReviewStatus.APPROVED : SiteLogReviewStatus.REJECTED);
        siteLog.setReviewedBy(payload.reviewedBy());
        siteLog.setReviewedAt(LocalDateTime.now());
        siteLog.setReviewerComments(payload.comments());
        siteLogRepository.save(siteLog);

        // Notify the site engineer who submitted the log
        String seUserId  = siteLog.getSubmittedBy();
        String projectId = siteLog.getProjectId();
        String logId     = siteLog.getLogId();

        String eventType = approved ? "SITE_LOG_APPROVED" : "SITE_LOG_REJECTED";
        String message = approved
                ? String.format("Your daily site log [%s] for project %s has been APPROVED by the Project Manager. Keep up the great work!",
                logId, projectId)
                : String.format("Your daily site log [%s] for project %s has been REJECTED by the Project Manager. Comments: %s",
                logId, projectId,
                payload.comments() != null ? payload.comments() : "No comments provided.");

        pushCentral(
                eventType,
                message,
                payload.reviewedBy(),               // fromUserId — the PM who decided
                "SITE_ENGINEER",
                seUserId,                            // toUserId — the SE who submitted
                payload.approvalId());

        log.info("SE '{}' notified of {} for site log '{}'", seUserId, payload.decision(), logId);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "logId", logId,
                "decision", payload.decision(),
                "seNotified", seUserId));
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * toUserId is required; if null/blank, the call is skipped.
     * Internal callback has no Authorization context, so we pass null.
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

    // ── DTO ──────────────────────────────────────────────────────────────────
    public record ApprovalResultRequest(
            String approvalId,
            String decision,   // "APPROVED" or "REJECTED"
            String reviewedBy,
            String comments
    ) {}
}