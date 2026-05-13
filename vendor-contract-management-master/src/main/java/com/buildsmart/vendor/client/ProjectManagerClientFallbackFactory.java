package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.PMClientApprovalRequest;
import com.buildsmart.vendor.client.dto.ProjectDTO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for {@link ProjectManagerClient}. Receives the original
 * cause (e.g. {@link FeignException.NotFound} for a 404 vs. a connect failure
 * for "PM down") so callers can distinguish the two situations.
 *
 * For methods where the distinction matters (currently only
 * {@code getProjectById}), the factory rethrows {@link FeignException.NotFound}
 * so the service layer can map it to a domain-level "project not found" error.
 * For all other failures it degrades gracefully like the legacy fallback.
 */
@Component
public class ProjectManagerClientFallbackFactory implements FallbackFactory<ProjectManagerClient> {

    private static final Logger log = LoggerFactory.getLogger(ProjectManagerClientFallbackFactory.class);

    @Override
    public ProjectManagerClient create(Throwable cause) {
        return new ProjectManagerClient() {

            @Override
            public Object createApprovalRequest(String authorization, PMClientApprovalRequest request) {
                log.warn("PM call failed - createApprovalRequest fallback triggered for approvalId={}, cause={}",
                        request.getApprovalId(), cause.getMessage());
                // Surface auth failures distinctly so callers/operators can act on them
                // instead of misdiagnosing them as PM downtime.
                if (cause instanceof feign.FeignException.Unauthorized
                        || cause instanceof feign.FeignException.Forbidden) {
                    throw new RuntimeException(
                            "Project Manager rejected the approval request (auth): " + cause.getMessage());
                }
                throw new RuntimeException(
                        "Project Manager service is currently unavailable. Please try submitting again later.");
            }

            @Override
            public java.util.List<Object> getPendingApprovals() {
                log.warn("PM unavailable - getPendingApprovals fallback triggered, cause={}", cause.getMessage());
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.List<com.buildsmart.vendor.client.dto.PMApprovalDTO> getAllApprovalsTyped(String authorization) {
                log.warn("PM unavailable - getAllApprovalsTyped fallback triggered, cause={}", cause.getMessage());
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.List<Object> getApprovalsByProject(String projectId) {
                log.warn("PM unavailable - getApprovalsByProject fallback triggered for projectId={}, cause={}",
                        projectId, cause.getMessage());
                return java.util.Collections.emptyList();
            }

            @Override
            public Object getApprovalStats() {
                log.warn("PM unavailable - getApprovalStats fallback triggered, cause={}", cause.getMessage());
                return java.util.Collections.singletonMap("error", "Stats currently unavailable. PM service is down.");
            }

            @Override
            public java.util.List<Object> getProjectTasks(String projectId) {
                log.warn("PM unavailable - getProjectTasks fallback triggered for projectId={}, cause={}",
                        projectId, cause.getMessage());
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.List<com.buildsmart.vendor.client.dto.PMTaskDTO> getProjectTasksTyped(String projectId) {
                // Distinguish "PM said 404" from "PM unreachable / generic failure".
                // - 404 → rethrow so the ownership service maps it to "Project not found".
                // - Anything else → return null so callers can degrade rather than
                //   falsely claim the vendor has no tasks (which would block writes
                //   during PM outages).
                if (cause instanceof FeignException.NotFound) {
                    log.info("PM returned 404 for getProjectTasksTyped, projectId={}", projectId);
                    throw (FeignException.NotFound) cause;
                }
                log.warn("PM unavailable - getProjectTasksTyped fallback triggered for projectId={}, cause={}",
                        projectId, cause.getMessage());
                return null;
            }

            @Override
            public void notifyContractCreated(String contractId, String vendorId, String projectId, String taskId) {
                log.warn("PM unavailable - notifyContractCreated fallback triggered for contractId={}, cause={}",
                        contractId, cause.getMessage());
                // Non-critical: vendor notification was already saved; PM will be informed when service is back.
            }

            @Override
            public ProjectDTO getProjectById(String projectId) {
                // Distinguish "PM said 404" from "PM unreachable / generic failure".
                // - 404 → rethrow so callers map it to ProjectNotFoundException.
                // - Anything else → return null so callers degrade gracefully (PM down
                //   should NOT block contract creation, mirroring the date-validator policy).
                if (cause instanceof FeignException.NotFound) {
                    log.info("PM returned 404 for projectId={}", projectId);
                    throw (FeignException.NotFound) cause;
                }
                log.warn("PM unavailable - getProjectById fallback triggered for projectId={}, cause={}",
                        projectId, cause.getMessage());
                return null;
            }

            @Override
            public void notifyDocumentSubmitted(String documentId, String contractId, String taskId, String documentName, String documentType, String submittedBy) {
                log.warn("PM unavailable - notifyDocumentSubmitted fallback triggered for documentId={}, cause={}", documentId, cause.getMessage());
                // Non-critical: vendor notification was already saved; PM will be informed when service is back.
            }

            @Override
            public void notifyDocumentApproved(String documentId, String approvalId, String approvedBy) {
                log.warn("PM unavailable - notifyDocumentApproved fallback triggered for documentId={}, cause={}", documentId, cause.getMessage());
                // Non-critical: vendor notification was already saved; PM will be informed when service is back.
            }

            @Override
            public void notifyDocumentRejected(String documentId, String approvalId, String rejectedBy, String rejectionReason) {
                log.warn("PM unavailable - notifyDocumentRejected fallback triggered for documentId={}, cause={}", documentId, cause.getMessage());
                // Non-critical: vendor notification was already saved; PM will be informed when service is back.
            }

            @Override
            public void notifyTaskAssigned(String vendorId, String taskId, String projectId, String description, String plannedStart, String plannedEnd, String assignedDepartment) {
                log.warn("PM unavailable - notifyTaskAssigned fallback triggered for vendorId={}, taskId={}, cause={}", vendorId, taskId, cause.getMessage());
                // Non-critical: vendor notification was already saved; PM will be informed when service is back.
            }

            @Override
            public void notifyInvoiceSubmitted(String invoiceId, String contractId, String taskId, java.math.BigDecimal amount, java.time.LocalDate date, String submittedBy) {
                log.warn("PM unavailable - notifyInvoiceSubmitted fallback triggered for invoiceId={}, cause={}", invoiceId, cause.getMessage());
                // Non-critical: vendor notification was already saved; PM will be informed when service is back.
            }
        };
    }
}
