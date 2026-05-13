package com.buildsmart.vendor.service;

import com.buildsmart.vendor.client.ProjectManagerClient;
import com.buildsmart.vendor.client.dto.PMApprovalDTO;
import com.buildsmart.vendor.entity.Document;
import com.buildsmart.vendor.entity.Invoice;
import com.buildsmart.vendor.enums.DocumentStatus;
import com.buildsmart.vendor.enums.InvoiceStatus;
import com.buildsmart.vendor.repository.DocumentRepository;
import com.buildsmart.vendor.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pulls approval decisions from the Project Manager module and reconciles
 * them back into the vendor module, firing the per-invoice / per-document
 * vendor notifications as a side effect.
 *
 * <p>The PM module does not push approve/reject decisions back to the
 * vendor (it has a {@code VendorServiceClient} but no PM service injects
 * it). Without a push, the vendor module would never learn that PM accepted
 * or rejected an approval. This service closes the loop on demand:</p>
 *
 * <ul>
 *   <li>{@link #syncAll(String)} — pulls every PM approval and reconciles.
 *       Wired to {@code POST /api/vendor-integration/sync-from-pm}.</li>
 *   <li>{@link #syncOne(String, String)} — reconciles a single approvalId.
 *       Wired into {@code GET /api/invoices/{id}/status} and
 *       {@code GET /api/documents/{id}/status} so a status check is
 *       self-healing and produces the vendor notification automatically.</li>
 * </ul>
 */
@Service
public class ApprovalSyncService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalSyncService.class);

    @Autowired
    private ProjectManagerClient projectManagerClient;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * Pull every approval from PM and reconcile any whose status has
     * advanced past PENDING. Returns a small report for the caller.
     */
    public SyncReport syncAll(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new RuntimeException("No authorization token available for PM sync.");
        }

        List<PMApprovalDTO> pmApprovals;
        try {
            pmApprovals = projectManagerClient.getAllApprovalsTyped(authorization);
        } catch (Exception e) {
            log.error("Failed to fetch approvals from PM during sync: {}", e.getMessage());
            throw new RuntimeException("Could not reach Project Manager service: " + e.getMessage());
        }

        int invoicesUpdated = 0;
        int documentsUpdated = 0;
        int skipped = 0;

        if (pmApprovals == null) {
            return new SyncReport(0, 0, 0);
        }

        for (PMApprovalDTO pmApproval : pmApprovals) {
            String approvalId = pmApproval.getApprovalId();
            String pmStatus = pmApproval.getStatus();
            if (approvalId == null || pmStatus == null) continue;
            if (!"ACCEPTED".equalsIgnoreCase(pmStatus) && !"REJECTED".equalsIgnoreCase(pmStatus)) {
                continue;
            }

            String pmActor = resolvePMActor(pmApproval);

            try {
                if (reconcileInvoice(approvalId, pmStatus, pmActor, pmApproval.getRejectionReason())) {
                    invoicesUpdated++;
                } else if (reconcileDocument(approvalId, pmStatus, pmActor, pmApproval.getRejectionReason())) {
                    documentsUpdated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Reconciliation failed for approvalId={}: {}", approvalId, e.getMessage());
                skipped++;
            }
        }

        return new SyncReport(invoicesUpdated, documentsUpdated, skipped);
    }

    /**
     * Reconcile a single approval ID. Used by status-check endpoints so a
     * vendor checking on a SUBMITTED invoice/document automatically picks up
     * PM's decision instead of seeing stale state.
     */
    public void syncOne(String approvalId, String authorization) {
        if (approvalId == null || approvalId.isBlank()) return;
        if (authorization == null || authorization.isBlank()) {
            log.debug("syncOne skipped for approvalId={}: no auth token available", approvalId);
            return;
        }
        try {
            List<PMApprovalDTO> all = projectManagerClient.getAllApprovalsTyped(authorization);
            if (all == null) return;
            for (PMApprovalDTO pm : all) {
                if (approvalId.equals(pm.getApprovalId())) {
                    String pmStatus = pm.getStatus();
                    if (!"ACCEPTED".equalsIgnoreCase(pmStatus) && !"REJECTED".equalsIgnoreCase(pmStatus)) {
                        return;
                    }
                    String pmActor = resolvePMActor(pm);
                    if (!reconcileInvoice(approvalId, pmStatus, pmActor, pm.getRejectionReason())) {
                        reconcileDocument(approvalId, pmStatus, pmActor, pm.getRejectionReason());
                    }
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("syncOne failed for approvalId={}: {}", approvalId, e.getMessage());
        }
    }

    private String resolvePMActor(PMApprovalDTO pm) {
        if (pm.getApprovedByName() != null && !pm.getApprovedByName().isBlank()) {
            return pm.getApprovedByName();
        }
        if (pm.getApprovedBy() != null && !pm.getApprovedBy().isBlank()) {
            return pm.getApprovedBy();
        }
        return "Project Manager";
    }

    private boolean reconcileInvoice(String approvalId, String pmStatus, String pmActor, String rejectionReason) {
        Invoice invoice = invoiceRepository.findByApprovalId(approvalId).orElse(null);
        if (invoice == null) return false;

        if ("ACCEPTED".equalsIgnoreCase(pmStatus)) {
            if (invoice.getStatus() == InvoiceStatus.SUBMITTED) {
                invoiceService.updateInvoiceApprovalStatusByApprovalId(approvalId, pmActor);
                log.info("Invoice {} reconciled to APPROVED by PM (approvalId={})", invoice.getInvoiceId(), approvalId);
            }
        } else if ("REJECTED".equalsIgnoreCase(pmStatus)) {
            if (invoice.getStatus() == InvoiceStatus.SUBMITTED) {
                invoiceService.updateInvoiceRejectionStatusByApprovalId(approvalId, pmActor,
                        rejectionReason != null ? rejectionReason : "Rejected by Project Manager");
                log.info("Invoice {} reconciled to REJECTED by PM (approvalId={})", invoice.getInvoiceId(), approvalId);
            }
        }
        return true;
    }

    private boolean reconcileDocument(String approvalId, String pmStatus, String pmActor, String rejectionReason) {
        Document document = documentRepository.findByApprovalId(approvalId).orElse(null);
        if (document == null) return false;

        if ("ACCEPTED".equalsIgnoreCase(pmStatus)) {
            if (document.getStatus() == DocumentStatus.SUBMITTED) {
                documentService.updateDocumentApprovalStatusByApprovalId(approvalId, pmActor);
                log.info("Document {} reconciled to APPROVED by PM (approvalId={})", document.getDocumentId(), approvalId);
            }
        } else if ("REJECTED".equalsIgnoreCase(pmStatus)) {
            if (document.getStatus() == DocumentStatus.SUBMITTED) {
                documentService.updateDocumentRejectionStatusByApprovalId(approvalId, pmActor,
                        rejectionReason != null ? rejectionReason : "Rejected by Project Manager");
                log.info("Document {} reconciled to REJECTED by PM (approvalId={})", document.getDocumentId(), approvalId);
            }
        }
        return true;
    }

    public record SyncReport(int invoicesUpdated, int documentsUpdated, int skipped) {}
}
