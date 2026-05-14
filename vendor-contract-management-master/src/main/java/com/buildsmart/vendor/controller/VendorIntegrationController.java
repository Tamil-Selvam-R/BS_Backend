package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.dto.response.AssignedTaskResponse;
import com.buildsmart.vendor.enums.AssignedTaskStatus;
import com.buildsmart.vendor.service.AssignedTaskService;
import com.buildsmart.vendor.service.DocumentService;
import com.buildsmart.vendor.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal integration controller called by the Project Manager module
 * to push approval/rejection decisions back into the Vendor module.
 *
 * Flow: PM approves/rejects → calls PUT /api/vendor-integration/approvals/{approvalId}/status
 *       → this controller updates invoice or document status accordingly
 *       → the central notification-service is pushed with the vendor as toUserId.
 *
 * The legacy local VendorNotificationService has been removed; every event
 * goes through the platform-wide notification-service.
 */
@RestController
@RequestMapping("/api/vendor-integration")
@Tag(name = "Vendor Integration (Internal)", description = "Internal service-to-service endpoints used by Project Manager, SiteOps, and Analytics. Most paths are open (no JWT) for inter-service calls.")
public class VendorIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(VendorIntegrationController.class);

    private final InvoiceService invoiceService;
    private final DocumentService documentService;
    private final com.buildsmart.vendor.client.ProjectManagerClient projectManagerClient;
    private final com.buildsmart.vendor.service.ApprovalSyncService approvalSyncService;
    private final com.buildsmart.vendor.service.DeliveryService deliveryService;
    private final AssignedTaskService assignedTaskService;

    /**
     * Central notification-service client. Replaces the deleted local
     * VendorNotificationService. Marked optional so unit tests / startup
     * without the bean still work.
     */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    public VendorIntegrationController(InvoiceService invoiceService,
                                       DocumentService documentService,
                                       com.buildsmart.vendor.client.ProjectManagerClient projectManagerClient,
                                       com.buildsmart.vendor.service.ApprovalSyncService approvalSyncService,
                                       com.buildsmart.vendor.service.DeliveryService deliveryService,
                                       AssignedTaskService assignedTaskService) {
        this.invoiceService = invoiceService;
        this.documentService = documentService;
        this.projectManagerClient = projectManagerClient;
        this.approvalSyncService = approvalSyncService;
        this.deliveryService = deliveryService;
        this.assignedTaskService = assignedTaskService;
    }

    /**
     * Pull every approval decision from PM and reconcile vendor state.
     *
     * <p>The Project Manager module currently does not push approve/reject
     * decisions back into the vendor module, so the vendor's invoices and
     * documents would otherwise stay {@code SUBMITTED} forever. Calling this
     * endpoint after PM acts (or on a schedule) updates vendor statuses and
     * fires the corresponding APPROVAL_ACCEPTED / APPROVAL_REJECTED
     * notifications to the vendor.</p>
     */
    @PostMapping("/sync-from-pm")
    public ResponseEntity<com.buildsmart.vendor.service.ApprovalSyncService.SyncReport> syncFromPM(
            jakarta.servlet.http.HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return ResponseEntity.ok(approvalSyncService.syncAll(authorization));
    }

    /**
     * Called by PM to notify vendor of approval/rejection decision.
     * Tries to match approvalId against invoices first, then documents.
     *
     * @param approvalId       the approval ID (APRVN-xxx format)
     * @param status           "APPROVED" or "REJECTED"
     * @param actionBy         the PM user ID who took the action
     * @param approvedByName   the PM user's display name (shown in vendor notification)
     * @param rejectionReason  mandatory when status is REJECTED; null when APPROVED
     */
    @PutMapping("/approvals/{approvalId}/status")
    public ResponseEntity<String> updateApprovalStatus(
            @PathVariable("approvalId") String approvalId,
            @RequestParam("status") String status,
            @RequestParam("rejectedBy") String actionBy,
            @RequestParam(value = "approvedByName", required = false) String approvedByName,
            @RequestParam(value = "rejectionReason", required = false) String rejectionReason) {

        if (approvalId == null || approvalId.isBlank()) {
            return ResponseEntity.badRequest().body("approvalId is required");
        }
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.APPROVAL, approvalId);

        // Use approvedByName in messages; fall back to actionBy if not provided
        String pmDisplayName = (approvedByName != null && !approvedByName.isBlank()) ? approvedByName : actionBy;

        log.info("Received approval status update from PM: approvalId={}, status={}, actionBy={}, pmName={}",
                approvalId, status, actionBy, pmDisplayName);

        boolean handled = false;

        if ("APPROVED".equalsIgnoreCase(status)) {
            try {
                invoiceService.updateInvoiceApprovalStatusByApprovalId(approvalId, pmDisplayName);
                log.info("Invoice approved for approvalId={}, approvedByName={}", approvalId, pmDisplayName);
                handled = true;
            } catch (RuntimeException invoiceEx) {
                log.debug("No invoice found for approvalId={}, trying documents. Reason: {}", approvalId, invoiceEx.getMessage());
                try {
                    documentService.updateDocumentApprovalStatusByApprovalId(approvalId, pmDisplayName);
                    log.info("Document approved for approvalId={}, approvedByName={}", approvalId, pmDisplayName);
                    handled = true;
                } catch (RuntimeException docEx) {
                    log.warn("No document found for approvalId={} either. Reason: {}", approvalId, docEx.getMessage());
                }
            }

        } else if ("REJECTED".equalsIgnoreCase(status)) {
            try {
                invoiceService.updateInvoiceRejectionStatusByApprovalId(approvalId, pmDisplayName, rejectionReason);
                log.info("Invoice rejected for approvalId={}, rejectedByName={}", approvalId, pmDisplayName);
                handled = true;
            } catch (RuntimeException invoiceEx) {
                log.debug("No invoice found for approvalId={}, trying documents. Reason: {}", approvalId, invoiceEx.getMessage());
                try {
                    documentService.updateDocumentRejectionStatusByApprovalId(approvalId, pmDisplayName, rejectionReason);
                    log.info("Document rejected for approvalId={}, rejectedByName={}", approvalId, pmDisplayName);
                    handled = true;
                } catch (RuntimeException docEx) {
                    log.warn("No document found for approvalId={} either. Reason: {}", approvalId, docEx.getMessage());
                }
            }

        } else {
            return ResponseEntity.badRequest().body("Invalid status: " + status + ". Must be APPROVED or REJECTED.");
        }

        if (!handled) {
            log.error("No invoice or document found for approvalId={}", approvalId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok("Approval status updated successfully");
    }

    /**
     * Proxy endpoint: Get tasks for a project from PM (used by vendor UI if needed).
     */
    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<java.util.List<Object>> getTasksForProject(@PathVariable("projectId") String projectId) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.PROJECT, projectId);
        return ResponseEntity.ok(projectManagerClient.getProjectTasks(projectId));
    }

    /**
     * Called by PM whenever a task is created/assigned to a vendor.
     * Pushes a TASK_ASSIGNED event to the central notification-service so the
     * vendor sees the new task in the unified bell icon with full context
     * (taskId, projectId, description, dates).
     *
     * PM endpoint that calls this: POST /api/vendor-integration/tasks/notify
     */
    @PostMapping("/tasks/notify")
    public ResponseEntity<String> notifyTaskAssigned(
            @RequestParam("vendorId") String vendorId,
            @RequestParam("taskId") String taskId,
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "plannedStart", required = false) String plannedStart,
            @RequestParam(value = "plannedEnd", required = false) String plannedEnd,
            @RequestParam(value = "assignedDepartment", required = false) String assignedDepartment) {

        if (vendorId == null || vendorId.isBlank()) {
            return ResponseEntity.badRequest().body("vendorId is required");
        }
        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.badRequest().body("taskId is required");
        }
        if (projectId == null || projectId.isBlank()) {
            return ResponseEntity.badRequest().body("projectId is required");
        }
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.VENDOR, vendorId);
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.TASK, taskId);
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.PROJECT, projectId);

        String message = String.format(
                "You have been assigned a new task (%s): %s. Planned: %s to %s",
                taskId,
                description != null ? description : "N/A",
                plannedStart != null ? plannedStart : "N/A",
                plannedEnd != null ? plannedEnd : "N/A"
        );

        try {
            // Push TASK_ASSIGNED to central notification-service. The vendor is
            // the recipient; we resolve no fromUserId here since the PM is the
            // sender but PM's userId isn't in this request — leave null and the
            // vendor's bell will show "from PROJECT_MANAGER".
            pushCentral("TASK_ASSIGNED",
                    message,
                    null,                                         // fromUserId — PM (anonymous in this hook)
                    "VENDOR", vendorId,                           // toUserId — the vendor
                    taskId);
            log.info("Vendor central notification pushed for task assignment: vendorId={}, taskId={}, projectId={}",
                    vendorId, taskId, projectId);

            // Notify Project Manager module as well (writes to PM's own local table
            // so the PM-side dashboard shows the assignment).
            projectManagerClient.notifyTaskAssigned(
                    vendorId, taskId, projectId, description, plannedStart, plannedEnd, assignedDepartment
            );
            log.info("PM notified of task assignment: vendorId={}, taskId={}, projectId={}",
                    vendorId, taskId, projectId);
            return ResponseEntity.ok("Task assignment notification stored for vendor " + vendorId);
        } catch (Exception e) {
            log.error("Failed to store task assignment notification for vendorId={}, taskId={}: {}",
                    vendorId, taskId, e.getMessage());
            return ResponseEntity.status(500).body("Failed to store notification: " + e.getMessage());
        }
    }

    /**
     * Manual pull-from-site fallback. Forces vendor to fetch the latest Site
     * Officer outcome from siteops and apply it to the local delivery row.
     * Useful when the original push from siteops to vendor was lost — the
     * regular {@code GET /api/deliveries/{id}} call also auto-syncs, but this
     * endpoint is provided for explicit refreshes.
     *
     * Path: POST /api/vendor-integration/deliveries/{deliveryId}/sync-from-site
     */
    @PostMapping("/deliveries/{deliveryId}/sync-from-site")
    public ResponseEntity<?> syncDeliveryFromSite(
            @PathVariable("deliveryId") String deliveryId) {
        try {
            com.buildsmart.vendor.dto.response.DeliveryResponse updated =
                    deliveryService.pullSiteStatus(deliveryId);
            log.info("Manual sync-from-site applied for delivery {}", deliveryId);
            return ResponseEntity.ok(updated);
        } catch (com.buildsmart.vendor.exception.CustomExceptions.DeliveryNotFoundException nf) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            log.error("sync-from-site failed for delivery {}: {}", deliveryId, ex.getMessage());
            return ResponseEntity.status(500)
                    .body(java.util.Map.of("error", "Failed to sync from site: " + ex.getMessage()));
        }
    }

    /**
     * FEATURE SET 2 step 2 — Site → Vendor callback.
     *
     * Path: PATCH /api/vendor-integration/deliveries/{deliveryId}/site-status
     * Open in SecurityConfig (no JWT) since this is a service-to-service call.
     *
     * Body params:
     *   status   — "RECEIVED" or "NOT_RECEIVED" (required)
     *   remarks  — optional free text from the Site Officer
     *
     * Updates the vendor delivery row's status so the vendor sees the outcome.
     */
    @PatchMapping("/deliveries/{deliveryId}/site-status")
    public ResponseEntity<?> updateDeliverySiteStatus(
            @PathVariable("deliveryId") String deliveryId,
            @RequestParam("status") String status,
            @RequestParam(value = "remarks", required = false) String remarks) {
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body("status is required (RECEIVED or NOT_RECEIVED)");
        }
        com.buildsmart.vendor.enums.DeliveryStatus parsed;
        try {
            parsed = com.buildsmart.vendor.enums.DeliveryStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid status '" + status + "'. Use RECEIVED or NOT_RECEIVED.");
        }
        try {
            com.buildsmart.vendor.dto.response.DeliveryResponse updated =
                    deliveryService.confirmFromSite(deliveryId, parsed, remarks);
            log.info("Site marked delivery {} as {}", deliveryId, parsed);
            return ResponseEntity.ok(updated);
        } catch (com.buildsmart.vendor.exception.CustomExceptions.DeliveryNotFoundException nf) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException badArg) {
            return ResponseEntity.badRequest().body(badArg.getMessage());
        } catch (Exception ex) {
            log.error("Failed to update delivery {} from site status callback: {}",
                    deliveryId, ex.getMessage());
            return ResponseEntity.status(500).body("Failed to update delivery: " + ex.getMessage());
        }
    }

    @Operation(
            summary = "List all assigned tasks (internal — analytics use)",
            description = "Returns all vendor-assigned tasks across every vendor. " +
                    "This endpoint is open (no JWT required) and is intended for the analytics/report service. " +
                    "Do not expose this to end-user clients.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list returned successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AssignedTaskResponse.class))))
    })
    @GetMapping("/tasks")
    public ResponseEntity<List<AssignedTaskResponse>> getAllAssignedTasks() {
        return ResponseEntity.ok(assignedTaskService.getAllTasks());
    }

    @Operation(
            summary = "List assigned tasks filtered by status (internal — analytics use)",
            description = "Returns all vendor-assigned tasks matching the given status. " +
                    "Valid values: PENDING, SUBMITTED, COMPLETED, REJECTED. " +
                    "This endpoint is open (no JWT required) and is intended for the analytics/report service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filtered task list returned successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AssignedTaskResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid status value", content = @Content)
    })
    @GetMapping("/tasks/status/{status}")
    public ResponseEntity<List<AssignedTaskResponse>> getAssignedTasksByStatus(
            @Parameter(description = "Task status filter: PENDING, SUBMITTED, COMPLETED, or REJECTED", required = true)
            @PathVariable("status") String status) {
        AssignedTaskStatus taskStatus;
        try {
            taskStatus = AssignedTaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(assignedTaskService.getAllTasksByStatus(taskStatus));
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