package com.buildsmart.projectmanager.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign clients used by PM's ApprovalService to push approve/reject callbacks
 * back to the originating downstream service.
 *
 * Each downstream service exposes:
 *   PATCH /api/<service>/tasks/internal/approval-result
 * with a body like:
 *   { "pmTaskId": "TASK001", "decision": "APPROVED|REJECTED", "rejectionReason": "..." }
 *
 * The callback is fire-and-forget from PM's perspective; failures are logged
 * but do not roll back the PM-side approval transaction.
 */
public final class TaskApprovalCallbackClients {

    private TaskApprovalCallbackClients() {}

    /**
     * Payload sent to every downstream callback.
     * <p>
     * The {@code approvalId} field was added so the downstream service can
     * reconcile records that are tracked by approvalId rather than taskId
     * (vendor invoices/documents in particular). Existing callbacks that
     * decode the payload as {@code Map<String, String>} simply ignore the
     * extra key, so this addition is backwards-compatible.
     */
    public record ApprovalResultPayload(
            String pmTaskId,
            String decision,          // "APPROVED" or "REJECTED"
            String rejectionReason,   // populated only on REJECTED
            String approvalId         // the PM approval id (e.g. APRVN-001)
    ) {}

    @FeignClient(name = "safety-service", contextId = "pmSafetyCallback")
    public interface SafetyCallbackClient {
        @PatchMapping("/api/safety/tasks/internal/approval-result")
        Map<String, Object> notifyApprovalResult(@RequestBody ApprovalResultPayload payload);
    }

    @FeignClient(name = "siteops-service", contextId = "pmSiteOpsCallback")
    public interface SiteOpsCallbackClient {
        @PatchMapping("/api/siteops/tasks/internal/approval-result")
        Map<String, Object> notifyApprovalResult(@RequestBody ApprovalResultPayload payload);
    }

    @FeignClient(name = "finance-service", contextId = "pmFinanceCallback")
    public interface FinanceCallbackClient {
        @PatchMapping("/api/finance/tasks/internal/approval-result")
        Map<String, Object> notifyApprovalResult(@RequestBody ApprovalResultPayload payload);
    }

    @FeignClient(name = "vendor-service", contextId = "pmVendorCallback")
    public interface VendorCallbackClient {
        @PatchMapping("/api/vendor/tasks/internal/approval-result")
        Map<String, Object> notifyApprovalResult(@RequestBody ApprovalResultPayload payload);
    }
}
