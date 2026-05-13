package com.buildsmart.siteops.client.fallback;

import com.buildsmart.siteops.client.ProjectManagerClient;
import com.buildsmart.siteops.client.dto.ProjectDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Resilience4j fallback factory for ProjectManagerClient.
 *
 * When the Project Manager service is down or slow, these fallback methods
 * are invoked — SiteOps logs the failure gracefully instead of throwing 503.
 *
 * Note: getProject() returns null so the service layer can detect the failure
 * and throw a meaningful error to the client rather than an unhandled exception.
 */
@Slf4j
@Component
public class ProjectManagerClientFallbackFactory implements FallbackFactory<ProjectManagerClient> {

    @Override
    public ProjectManagerClient create(Throwable cause) {
        log.warn("Project Manager service is unavailable — using fallback. Reason: {}", cause.getMessage());
        return new ProjectManagerClient() {

            @Override
            public ProjectDto getProject(String projectId, String authorization) {
                log.warn("PM fallback: getProject({}) — project-service unreachable.", projectId);
                // Returning null signals to SiteLogServiceImpl / IssueServiceImpl
                // that PM is down; they will throw a clear 503 ServiceUnavailableException.
                return null;
            }

            @Override
            public void notifyPMSiteLogSubmitted(SiteLogNotificationPayload payload, String authorization) {
                // Fire-and-forget — PM notification failure must NEVER block site log creation.
                log.warn("PM fallback: notifyPMSiteLogSubmitted(logId={}) — notification skipped, PM unreachable.",
                        payload.logId());
            }

            @Override
            public void notifyPMIssueSubmitted(IssueNotificationPayload payload, String authorization) {
                // Fire-and-forget — PM notification failure must NEVER block issue creation.
                log.warn("PM fallback: notifyPMIssueSubmitted(issueId={}) — notification skipped, PM unreachable.",
                        payload.issueId());
            }

            @Override
            public Object createApprovalRequest(ApprovalCreateRequest payload, String authorization) {
                log.warn("PM fallback: createApprovalRequest(approvalId={}) — PM unreachable. Local site-log "
                        + "is still SUBMITTED; reconcile via re-submit once PM is back.", payload.approvalId());
                return null;
            }

            @Override
            public Object updateMilestonesByProgress(String projectId,
                                                     java.util.Map<String, java.math.BigDecimal> body,
                                                     String authorization) {
                log.warn("PM fallback: updateMilestonesByProgress(projectId={}) — PM unreachable. "
                        + "Site log is still saved locally; milestones will sync on the next site log.",
                        projectId);
                return null;
            }
        };
    }
}

