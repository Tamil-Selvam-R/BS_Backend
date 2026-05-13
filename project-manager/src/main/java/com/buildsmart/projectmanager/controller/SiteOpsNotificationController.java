package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound notification hooks called by the SiteOps service via Feign.
 *
 * SiteOps pushes site-log and issue events here so the PM's internal
 * notification table is updated and the PM can see them at
 * GET /notifications/me — exactly as task-submission events appear.
 *
 * Endpoints are under /notifications/siteops/** which falls inside the
 * SecurityConfig rule:
 *   .requestMatchers("/notifications/**").hasAnyRole("ADMIN","PROJECT_MANAGER",
 *       "VENDOR","SITE_ENGINEER","SAFETY_OFFICER","FINANCE_OFFICER")
 * so no additional security changes are needed.
 */
@Slf4j
@RestController
@RequestMapping("/notifications/siteops")
@RequiredArgsConstructor
public class SiteOpsNotificationController {

    private final NotificationService notificationService;

    /**
     * Called by SiteOps when a Site Engineer submits a daily site log for PM review.
     * Writes an APPROVAL_REQUIRED notification into the PM's notification table.
     */
    @PostMapping("/site-log-submitted")
    public ResponseEntity<Void> onSiteLogSubmitted(@RequestBody SiteLogNotificationPayload payload) {
        try {
            notificationService.notifySiteLogSubmitted(
                    payload.logId(),
                    payload.projectId(),
                    payload.submittedBy(),
                    payload.approvalId(),
                    payload.activitiesSummary());
            log.info("PM notification saved: site log [{}] submitted by {} on project {}",
                    payload.logId(), payload.submittedBy(), payload.projectId());
        } catch (Exception ex) {
            // Non-blocking — SiteOps must not roll back a site log submission
            // just because the PM notification write failed.
            log.warn("Failed to save PM notification for site log [{}]: {}",
                    payload.logId(), ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Called by SiteOps when a Site Engineer reports an issue.
     * Writes an APPROVAL_REQUIRED notification into the PM's notification table.
     */
    @PostMapping("/issue-submitted")
    public ResponseEntity<Void> onIssueSubmitted(@RequestBody IssueNotificationPayload payload) {
        try {
            notificationService.notifyIssueSubmitted(
                    payload.issueId(),
                    payload.projectId(),
                    payload.reportedBy(),
                    payload.severity(),
                    payload.description());
            log.info("PM notification saved: issue [{}] reported by {} on project {}",
                    payload.issueId(), payload.reportedBy(), payload.projectId());
        } catch (Exception ex) {
            log.warn("Failed to save PM notification for issue [{}]: {}",
                    payload.issueId(), ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ── Payload records — mirror SiteOps' ProjectManagerClient inner records ──

    public record SiteLogNotificationPayload(
            String logId,
            String projectId,
            String submittedBy,
            String approvalId,
            String activitiesSummary
    ) {}

    public record IssueNotificationPayload(
            String issueId,
            String projectId,
            String logId,
            String reportedBy,
            String severity,
            String description,
            String approvalId
    ) {}
}
