package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.dto.response.AssignedTaskResponse;
import com.buildsmart.vendor.dto.response.AssignedTaskSyncResult;
import com.buildsmart.vendor.enums.AssignedTaskStatus;
import com.buildsmart.vendor.service.AssignedTaskService;
import com.buildsmart.vendor.service.DocumentService;
import com.buildsmart.vendor.service.InvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vendor-facing tasks controller.
 *
 * Notification policy: the legacy local VendorNotificationService has been
 * removed. The approval-result callback now pushes APPROVAL_ACCEPTED /
 * APPROVAL_REJECTED to the central notification-service so the vendor sees
 * the outcome from the unified bell icon.
 */
@RestController
@RequestMapping("/api/vendor/tasks")
@RequiredArgsConstructor
@Slf4j
public class AssignedTaskController {

    private final AssignedTaskService assignedTaskService;
    /**
     * Used by the approval-result callback to flip Invoice / Document status
     * for vendor approvals. PM's callback only carries pmTaskId; we additionally
     * read approvalId from the payload so we can reconcile invoices and documents
     * that are tracked by approvalId.
     */
    private final InvoiceService invoiceService;
    private final DocumentService documentService;
    /**
     * Central notification-service client. Replaces the deleted local
     * VendorNotificationService — every approval-result event is now routed
     * to the specific vendor by toUserId.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    public ResponseEntity<AssignedTaskSyncResult> sync(HttpServletRequest request) {
        return ResponseEntity.ok(assignedTaskService.syncTasksFromPm(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasks(
            HttpServletRequest request,
            @RequestParam(required = false) AssignedTaskStatus status) {
        if (status != null) {
            return ResponseEntity.ok(assignedTaskService.getMyTasksByStatus(request, status));
        }
        return ResponseEntity.ok(assignedTaskService.getMyTasks(request));
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasksForProject(
            HttpServletRequest request,
            @PathVariable String projectId) {
        return ResponseEntity.ok(assignedTaskService.getMyTasksForProject(request, projectId));
    }

    /**
     * Submit a vendor task to PM for approval. Local status moves to SUBMITTED;
     * PM's approve/reject decision arrives via /internal/approval-result.
     * Body MUST contain a non-blank 'description' field explaining the work done.
     */
    @PostMapping("/{assignedTaskId}/submit")
    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    public ResponseEntity<AssignedTaskResponse> submitTask(
            HttpServletRequest request,
            @PathVariable String assignedTaskId,
            @RequestBody java.util.Map<String, String> body) {
        // Description is mandatory: the vendor must explain what they did
        // before PM can review the work in GET /api/approvals.
        if (body == null) {
            throw new IllegalArgumentException(
                    "Request body is required. Provide a 'description' explaining the work done.");
        }
        String description = body.getOrDefault("description", body.get("remarks"));
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "'description' is required and must explain the work done before submission.");
        }
        return ResponseEntity.ok(assignedTaskService.submitTask(request, assignedTaskId, description));
    }

    /**
     * Internal callback invoked by PM (via Feign) after approve/reject.
     * Body: { "pmTaskId": "TASK001", "decision": "APPROVED|REJECTED",
     *         "rejectionReason": "...", "approvalId": "APRVN-001" }
     *
     * Behaviour:
     *  - Always updates the AssignedTask.
     *  - Pushes APPROVAL_ACCEPTED / APPROVAL_REJECTED to the central
     *    notification-service so the vendor sees the outcome immediately.
     *  - If {@code approvalId} is present, reconciles any Invoice or Document
     *    that was submitted with that approvalId.
     *    APPROVED → status becomes APPROVED, vendor notified APPROVAL_ACCEPTED.
     *    REJECTED → status becomes REJECTED with reason, vendor notified APPROVAL_REJECTED.
     *
     * Failures on the invoice/document reconciliation are logged but do not
     * fail the callback so the AssignedTask part still succeeds.
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
        AssignedTaskResponse taskResponse = assignedTaskService.handleApprovalResult(
                pmTaskId, decision, rejectionReason);

        // 1.5) Push the outcome to the central notification-service so the
        //      vendor sees it from the unified bell icon. toUserId is the
        //      vendor (the task's assignedTo).
        try {
            boolean taskApproved = "APPROVED".equalsIgnoreCase(decision);
            String eventType = taskApproved ? "APPROVAL_ACCEPTED" : "APPROVAL_REJECTED";
            String notifMsg = taskApproved
                    ? "Your vendor task [" + pmTaskId + "] has been APPROVED by the Project Manager"
                    + " and is now marked COMPLETED. Well done!"
                    : "Your vendor task [" + pmTaskId + "] was REJECTED by the Project Manager."
                    + " Reason: " + (rejectionReason == null || rejectionReason.isBlank()
                    ? "(no reason given)" : rejectionReason)
                    + ". Please rework and resubmit.";
            pushCentral(
                    eventType,
                    notifMsg,
                    null,                                          // fromUserId — PM but anonymous in this callback
                    "VENDOR",
                    taskResponse.assignedTo(),                     // toUserId — the vendor
                    pmTaskId);
        } catch (Exception ex) {
            log.warn("Central notification push failed for pmTaskId={}: {}", pmTaskId, ex.getMessage());
        }

        // 2) Reconcile Invoice/Document if approvalId is supplied. Best-effort:
        // an approval may belong to a Task only (no invoice/document) or to either
        // type, so each lookup is independently guarded.
        if (approvalId != null && !approvalId.isBlank()) {
            String pmDisplayName = "Project Manager";
            boolean approved = "APPROVED".equalsIgnoreCase(decision);
            if (approved) {
                try {
                    invoiceService.updateInvoiceApprovalStatusByApprovalId(approvalId, pmDisplayName);
                    log.info("Invoice reconciled APPROVED for approvalId={}", approvalId);
                } catch (RuntimeException invoiceEx) {
                    log.debug("No invoice for approvalId={}, trying documents. ({})",
                            approvalId, invoiceEx.getMessage());
                    try {
                        documentService.updateDocumentApprovalStatusByApprovalId(approvalId, pmDisplayName);
                        log.info("Document reconciled APPROVED for approvalId={}", approvalId);
                    } catch (RuntimeException docEx) {
                        log.debug("No document for approvalId={} either. ({})",
                                approvalId, docEx.getMessage());
                    }
                }
            } else {
                try {
                    invoiceService.updateInvoiceRejectionStatusByApprovalId(
                            approvalId, pmDisplayName, rejectionReason);
                    log.info("Invoice reconciled REJECTED for approvalId={}", approvalId);
                } catch (RuntimeException invoiceEx) {
                    log.debug("No invoice for approvalId={}, trying documents. ({})",
                            approvalId, invoiceEx.getMessage());
                    try {
                        documentService.updateDocumentRejectionStatusByApprovalId(
                                approvalId, pmDisplayName, rejectionReason);
                        log.info("Document reconciled REJECTED for approvalId={}", approvalId);
                    } catch (RuntimeException docEx) {
                        log.debug("No document for approvalId={} either. ({})",
                                approvalId, docEx.getMessage());
                    }
                }
            }
        }

        return ResponseEntity.ok(taskResponse);
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * toUserId is required; if null/blank, the call is skipped.
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
            notificationServiceClient.create(new NotificationCreateRequest(
                    eventType,
                    message,
                    "vendor-service",
                    "PROJECT_MANAGER",
                    fromUserId,
                    toRole,
                    toUserId,
                    referenceId,
                    null
            ));
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
}