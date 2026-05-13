package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.entity.ApprovalRequest;
import com.buildsmart.projectmanager.entity.ProjectTask;
import com.buildsmart.projectmanager.repository.ApprovalRequestRepository;
import com.buildsmart.projectmanager.repository.ProjectTaskRepository;
import com.buildsmart.projectmanager.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound notification hooks called by the Vendor service via Feign.
 *
 * Every endpoint the vendor's ProjectManagerClient declares (contract-created,
 * document-submitted, document-approved, document-rejected, invoice-submitted)
 * lands here and writes a notification into the PM's internal notifications
 * table so the PM sees it at GET /notifications/me.
 *
 * All paths fall under /notifications/** which SecurityConfig already allows
 * for VENDOR role — no security changes needed.
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class VendorNotificationController {

    private final NotificationService notificationService;
    private final ProjectTaskRepository taskRepository;
    private final ApprovalRequestRepository approvalRepository;

    // ── 1. Contract created ───────────────────────────────────────────────────

    /**
     * Called by vendor when a new contract is created.
     * Params: contractId, vendorId, projectId, taskId (optional)
     */
    @PostMapping("/contract-created")
    public ResponseEntity<Void> onContractCreated(
            @RequestParam String contractId,
            @RequestParam String vendorId,
            @RequestParam String projectId,
            @RequestParam(required = false) String taskId) {
        try {
            String pmUserId = resolvePmUserIdFromTask(taskId);
            String taskNote = taskId != null ? " (Task: " + taskId + ")" : "";
            notificationService.notifyVendor(
                    projectId, pmUserId, vendorId,
                    "PROJECT_UPDATE",
                    "New Vendor Contract: " + contractId,
                    "Vendor [" + vendorId + "] created contract [" + contractId + "]"
                            + " for project " + projectId + "." + taskNote);
            log.info("PM notified: contract {} created by vendor {} on project {}", contractId, vendorId, projectId);
        } catch (Exception ex) {
            log.warn("PM notification failed for contract-created [{}]: {}", contractId, ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ── 2. Document submitted ─────────────────────────────────────────────────

    /**
     * Called by vendor when a document is submitted for review.
     * Params: documentId, contractId, taskId (optional), documentName, documentType, submittedBy
     */
    @PostMapping("/document-submitted")
    public ResponseEntity<Void> onDocumentSubmitted(
            @RequestParam String documentId,
            @RequestParam String contractId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String documentName,
            @RequestParam(required = false) String documentType,
            @RequestParam String submittedBy) {
        try {
            ProjectTask task = resolveTask(taskId);
            if (task == null) {
                log.warn("PM notification skipped for document-submitted [{}]: task {} not found", documentId, taskId);
                return ResponseEntity.ok().build();
            }
            String typeNote = documentType != null ? " [" + documentType + "]" : "";
            String nameNote = documentName != null ? " '" + documentName + "'" : "";
            notificationService.notifyVendor(
                    task.getProject().getProjectId(), task.getAssignedBy(), submittedBy,
                    "APPROVAL_REQUIRED",
                    "Vendor Document Submitted: " + documentId,
                    "Vendor " + submittedBy + " submitted document [" + documentId + "]"
                            + typeNote + nameNote + " on contract " + contractId
                            + ". Awaiting your review.");
            log.info("PM notified: document {} submitted by {} (task {})", documentId, submittedBy, taskId);
        } catch (Exception ex) {
            log.warn("PM notification failed for document-submitted [{}]: {}", documentId, ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ── 3. Document approved ──────────────────────────────────────────────────

    /**
     * Called by vendor when a document is approved internally.
     * Params: documentId, approvalId, approvedBy
     */
    @PostMapping("/document-approved")
    public ResponseEntity<Void> onDocumentApproved(
            @RequestParam String documentId,
            @RequestParam String approvalId,
            @RequestParam String approvedBy) {
        try {
            ApprovalRequest approval = resolveApproval(approvalId);
            if (approval == null) {
                log.warn("PM notification skipped for document-approved [{}]: approval {} not found", documentId, approvalId);
                return ResponseEntity.ok().build();
            }
            ProjectTask task = approval.getTask();
            String projectId = approval.getProject().getProjectId();
            String pmUserId = task != null ? task.getAssignedBy() : approval.getProject().getCreatedBy();
            notificationService.notifyVendor(
                    projectId, pmUserId, approvedBy,
                    "PROJECT_UPDATE",
                    "Vendor Document Approved: " + documentId,
                    "Document [" + documentId + "] (Approval: " + approvalId
                            + ") has been APPROVED by " + approvedBy + ".");
            log.info("PM notified: document {} approved (approvalId {})", documentId, approvalId);
        } catch (Exception ex) {
            log.warn("PM notification failed for document-approved [{}]: {}", documentId, ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ── 4. Document rejected ──────────────────────────────────────────────────

    /**
     * Called by vendor when a document is rejected.
     * Params: documentId, approvalId, rejectedBy, rejectionReason
     */
    @PostMapping("/document-rejected")
    public ResponseEntity<Void> onDocumentRejected(
            @RequestParam String documentId,
            @RequestParam String approvalId,
            @RequestParam String rejectedBy,
            @RequestParam(required = false) String rejectionReason) {
        try {
            ApprovalRequest approval = resolveApproval(approvalId);
            if (approval == null) {
                log.warn("PM notification skipped for document-rejected [{}]: approval {} not found", documentId, approvalId);
                return ResponseEntity.ok().build();
            }
            ProjectTask task = approval.getTask();
            String projectId = approval.getProject().getProjectId();
            String pmUserId = task != null ? task.getAssignedBy() : approval.getProject().getCreatedBy();
            String reason = (rejectionReason != null && !rejectionReason.isBlank())
                    ? " Reason: " + rejectionReason : "";
            notificationService.notifyVendor(
                    projectId, pmUserId, rejectedBy,
                    "PROJECT_UPDATE",
                    "Vendor Document Rejected: " + documentId,
                    "Document [" + documentId + "] (Approval: " + approvalId
                            + ") was REJECTED by " + rejectedBy + "." + reason);
            log.info("PM notified: document {} rejected (approvalId {})", documentId, approvalId);
        } catch (Exception ex) {
            log.warn("PM notification failed for document-rejected [{}]: {}", documentId, ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ── 5. Invoice submitted ──────────────────────────────────────────────────

    /**
     * Called by vendor when an invoice is submitted.
     * Params: invoiceId, contractId, taskId (optional), amount, date, submittedBy
     */
    @PostMapping("/invoice-submitted")
    public ResponseEntity<Void> onInvoiceSubmitted(
            @RequestParam String invoiceId,
            @RequestParam String contractId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) LocalDate date,
            @RequestParam String submittedBy) {
        try {
            ProjectTask task = resolveTask(taskId);
            if (task == null) {
                log.warn("PM notification skipped for invoice-submitted [{}]: task {} not found", invoiceId, taskId);
                return ResponseEntity.ok().build();
            }
            String amountNote = amount != null ? " Amount: " + amount + "." : "";
            String dateNote = date != null ? " Date: " + date + "." : "";
            notificationService.notifyVendor(
                    task.getProject().getProjectId(), task.getAssignedBy(), submittedBy,
                    "APPROVAL_REQUIRED",
                    "Vendor Invoice Submitted: " + invoiceId,
                    "Vendor " + submittedBy + " submitted invoice [" + invoiceId + "]"
                            + " on contract " + contractId + "." + amountNote + dateNote
                            + " Awaiting your review.");
            log.info("PM notified: invoice {} submitted by {} (task {})", invoiceId, submittedBy, taskId);
        } catch (Exception ex) {
            log.warn("PM notification failed for invoice-submitted [{}]: {}", invoiceId, ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Looks up a ProjectTask by taskId. Returns null if taskId is blank or task not found. */
    private ProjectTask resolveTask(String taskId) {
        if (taskId == null || taskId.isBlank()) return null;
        return taskRepository.findByTaskId(taskId).orElse(null);
    }

    /** Looks up an ApprovalRequest by approvalId. Returns null if not found. */
    private ApprovalRequest resolveApproval(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) return null;
        return approvalRepository.findByApprovalId(approvalId).orElse(null);
    }

    /** Resolves the PM's userId from the task's assignedBy field. Returns null if not found. */
    private String resolvePmUserIdFromTask(String taskId) {
        ProjectTask task = resolveTask(taskId);
        return task != null ? task.getAssignedBy() : null;
    }
}
