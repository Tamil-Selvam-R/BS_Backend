package com.buildsmart.vendor.service.impl;

import com.buildsmart.vendor.dto.response.InvoiceResponse;
import com.buildsmart.vendor.dto.request.InvoiceRequest;
import com.buildsmart.vendor.enums.InvoiceStatus;
import com.buildsmart.vendor.exception.CustomExceptions.InvoiceNotFoundException;
import com.buildsmart.vendor.entity.Invoice;
import com.buildsmart.vendor.repository.InvoiceRepository;
import com.buildsmart.vendor.repository.DocumentRepository;
import com.buildsmart.vendor.repository.ContractRepository;
import com.buildsmart.vendor.entity.Contract;
import com.buildsmart.vendor.service.InvoiceService;
import com.buildsmart.vendor.util.IdGeneratorUtil;
import com.buildsmart.vendor.validator.InvoiceValidator;
import com.buildsmart.vendor.client.ProjectManagerClient;
import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.client.dto.PMClientApprovalRequest;
import com.buildsmart.vendor.client.dto.ProjectDTO;
import com.buildsmart.vendor.entity.RevisedUpdate;
import com.buildsmart.vendor.enums.EntityType;
import com.buildsmart.vendor.repository.RevisedUpdateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local VendorNotificationService has been
 * removed — vendors read their notifications from the central service.
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private InvoiceValidator invoiceValidator;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private com.buildsmart.vendor.validator.ProjectDateValidator projectDateValidator;

    @Autowired
    private ProjectManagerClient projectManagerClient;

    @Autowired
    private RevisedUpdateRepository revisedUpdateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /** FEATURE SET 6 — push invoice events to the dedicated notification service. */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    @Override
    public Page<InvoiceResponse> getAllInvoices(Pageable pageable) {
        return invoiceRepository.findAll(pageable).map(this::toDTO);
    }

    @Override
    public InvoiceResponse getInvoiceById(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        return toDTO(invoice);
    }

    @Override
    public List<InvoiceResponse> getInvoicesByContractId(String contractId) {
        List<Invoice> invoices = invoiceRepository.findByContractId(contractId);
        List<InvoiceResponse> dtoList = new ArrayList<>();
        for (Invoice invoice : invoices) {
            dtoList.add(toDTO(invoice));
        }
        return dtoList;
    }

    @Override
    public List<InvoiceResponse> getInvoicesByStatus(InvoiceStatus status) {
        List<Invoice> invoices = invoiceRepository.findByStatus(status);
        List<InvoiceResponse> dtoList = new ArrayList<>();
        for (Invoice invoice : invoices) {
            dtoList.add(toDTO(invoice));
        }
        return dtoList;
    }

    @Override
    public InvoiceResponse createInvoice(InvoiceRequest request, String submittedBy) {
        invoiceValidator.validate(request);

        // Project-window enforcement (Item #3): invoice date must fall inside
        // the project window. Look up the parent project via contract.
        Contract parentContract = contractRepository.findById(request.getContractId()).orElse(null);
        if (parentContract != null && parentContract.getProjectId() != null) {
            projectDateValidator.validateDateWithinProject(
                    parentContract.getProjectId(), request.getDate(), "Invoice date");
        }

        // Amount-cap enforcement (Item #6): cumulative non-rejected invoices
        // for this contract plus the new invoice's amount must not exceed
        // contract.value. We exclude no existing invoice (this is a create).
        enforceContractValueCap(parentContract, request.getAmount(), null);

        String lastId = invoiceRepository.findTopByOrderByInvoiceIdDesc()
                .map(Invoice::getInvoiceId)
                .orElse(null);
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(IdGeneratorUtil.nextInvoiceId(lastId));
        invoice.setContractId(request.getContractId());
        invoice.setAmount(request.getAmount());
        invoice.setDate(request.getDate());
        invoice.setDescription(request.getDescription());
        invoice.setSubmittedBy(submittedBy);

        // Auto-fill taskId from Contract
        if (request.getTaskId() == null || request.getTaskId().isBlank()) {
            contractRepository.findById(request.getContractId())
                    .ifPresent(contract -> invoice.setTaskId(contract.getTaskId()));
        } else {
            invoice.setTaskId(request.getTaskId());
        }

        invoice.setSubmittedOn(LocalDateTime.now());
        invoice.setStatus(InvoiceStatus.PENDING);
        Invoice saved = invoiceRepository.save(invoice);

        // Notify PM module (writes to PM's local notification table)
        try {
            projectManagerClient.notifyInvoiceSubmitted(
                    saved.getInvoiceId(),
                    saved.getContractId(),
                    saved.getTaskId(),
                    saved.getAmount(),
                    saved.getDate(),
                    saved.getSubmittedBy()
            );
            log.info("PM notified of new invoice: invoiceId={}, contractId={}, taskId={}",
                    saved.getInvoiceId(), saved.getContractId(), saved.getTaskId());
        } catch (Exception e) {
            log.warn("Failed to notify PM of invoice submission (non-critical): invoiceId={}, reason={}",
                    saved.getInvoiceId(), e.getMessage());
        }

        // --- Push INVOICE_CREATED to central notification-service ---
        // Vendor echo only at this stage; the SUBMITTED event (sent in submitInvoice)
        // is what notifies the PM. Resolves vendorId via the contract.
        String vendorId = resolveVendorIdForNotification(saved);
        if (vendorId != null && !vendorId.isBlank()) {
            pushCentral(
                    "INVOICE_CREATED",
                    String.format("Invoice %s created for contract %s, amount %s.",
                            saved.getInvoiceId(), saved.getContractId(), saved.getAmount()),
                    vendorId,                                     // fromUserId — vendor (self)
                    "VENDOR", vendorId,                           // toUserId — vendor (echo)
                    saved.getInvoiceId());
        }

        return toDTO(saved);
    }

    @Override
    public InvoiceResponse updateInvoice(String id, InvoiceRequest request) {
        invoiceValidator.validate(request);

        // Project-window enforcement (Item #3): updated invoice date must also
        // fall inside the parent project's window.
        Contract parentContract = contractRepository.findById(request.getContractId()).orElse(null);
        if (parentContract != null && parentContract.getProjectId() != null) {
            projectDateValidator.validateDateWithinProject(
                    parentContract.getProjectId(), request.getDate(), "Invoice date");
        }

        // Amount-cap enforcement (Item #6): same rule as create, but exclude
        // THIS invoice's existing amount from the "already billed" sum since
        // we're replacing it. Without this, updating an invoice from 40k → 40k
        // would double-count.
        enforceContractValueCap(parentContract, request.getAmount(), id);

        Invoice existing = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        existing.setContractId(request.getContractId());
        existing.setAmount(request.getAmount());
        existing.setDate(request.getDate());
        existing.setDescription(request.getDescription());

        // Auto-fill taskId from Contract
        if (request.getTaskId() == null || request.getTaskId().isBlank()) {
            contractRepository.findById(request.getContractId())
                    .ifPresent(contract -> existing.setTaskId(contract.getTaskId()));
        } else {
            existing.setTaskId(request.getTaskId());
        }

        // If updating a REJECTED invoice, record the revision and reset status to PENDING
        if (existing.getStatus() == InvoiceStatus.REJECTED) {
            String previousRejectionReason = existing.getRejectionReason();

            try {
                RevisedUpdate revisedUpdate = new RevisedUpdate();
                revisedUpdate.setEntityType(EntityType.INVOICE);
                revisedUpdate.setOriginalEntityId(existing.getInvoiceId());
                revisedUpdate.setRejectionReason(previousRejectionReason);
                revisedUpdate.setPreviousData(objectMapper.writeValueAsString(existing));
                revisedUpdate.setUpdatedData(objectMapper.writeValueAsString(request));
                revisedUpdateRepository.save(revisedUpdate);
            } catch (Exception e) {
                log.warn("Could not save revision record for invoice {}: {}", id, e.getMessage());
            }

            existing.setStatus(InvoiceStatus.PENDING);
            existing.setRejectionReason(null);
            existing.setRejectedBy(null);
            existing.setRejectedOn(null);

            Invoice updated = invoiceRepository.save(existing);

            // --- Push APPROVAL_REJECTED follow-up to the vendor ---
            // The vendor needs to see the rejected → pending reset so they know to resubmit.
            String vendorId = resolveVendorIdForNotification(updated);
            if (vendorId != null && !vendorId.isBlank()) {
                pushCentral(
                        "APPROVAL_REJECTED",
                        "Invoice " + id + " has been updated after rejection. Reason was: "
                                + previousRejectionReason + ". Please resubmit for approval.",
                        null,                                     // fromUserId — system reset
                        "VENDOR", vendorId,                       // toUserId — the vendor
                        updated.getInvoiceId());
            }

            return toDTO(updated);
        }

        Invoice updated = invoiceRepository.save(existing);
        return toDTO(updated);
    }

    @Override
    public void deleteInvoice(String id) {
        invoiceRepository.deleteById(id);
    }

    @Override
    public InvoiceResponse submitInvoice(String id, String submittedBy, String authorization) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));

        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING invoices can be submitted. Current status: " + invoice.getStatus()
                            + ". If previously rejected, update the invoice first to reset it to PENDING.");
        }

        // Generate a fresh approval ID. For already-affected rejected invoices, recover
        // the last approvalId from revision history so resubmission does not reuse it.
        String approvalId = IdGeneratorUtil.nextApprovalId(resolveLatestApprovalIdBase(invoice, resolveGlobalMaxApprovalId()));

        invoice.setStatus(InvoiceStatus.SUBMITTED);
        invoice.setSubmittedBy(submittedBy);
        invoice.setSubmittedOn(LocalDateTime.now());
        invoice.setApprovalId(approvalId);
        Invoice saved = invoiceRepository.save(invoice);

        // Fetch Project ID from contract
        Contract contract = contractRepository.findById(invoice.getContractId()).orElse(null);
        String projectId = (contract != null && contract.getProjectId() != null) ? contract.getProjectId() : "UNKNOWN";
        String taskId = (invoice.getTaskId() != null && !invoice.getTaskId().isBlank()) ? invoice.getTaskId() : "UNKNOWN_TASK";
        String pmUserId = resolvePmUserId(projectId);
        String vendorId = resolveVendorIdForNotification(saved);

        try {
            // Build the PM approval request
            PMClientApprovalRequest pmRequest = new PMClientApprovalRequest();
            pmRequest.setApprovalId(approvalId);
            pmRequest.setProjectId(projectId);
            pmRequest.setTaskId(taskId);
            pmRequest.setApprovalType("VENDOR");
            pmRequest.setDescription("Invoice submitted for approval by " + submittedBy + ". Invoice ID: " + invoice.getInvoiceId()
                    + ". Amount: " + invoice.getAmount() + ". " + (invoice.getDescription() != null ? invoice.getDescription() : ""));
            pmRequest.setAmount(invoice.getAmount() != null ? invoice.getAmount().doubleValue() : 0.0);
            pmRequest.setRequestedBy(submittedBy);
            pmRequest.setRequestedByDepartment("VENDOR");

            // Pass the caller's JWT explicitly. The Feign RequestInterceptor reads
            // RequestContextHolder, which is empty on the Resilience4j TimeLimiter
            // worker thread — so without this, PM would receive no Authorization
            // header and reject the call with 401.
            projectManagerClient.createApprovalRequest(authorization, pmRequest);
            log.info("Approval request sent to PM for invoiceId={}, approvalId={}", invoice.getInvoiceId(), approvalId);

            // --- Push INVOICE_SUBMITTED to PM and vendor (echo) ---
            String submitMsg = "Invoice " + invoice.getInvoiceId()
                    + " submitted for approval. Approval ID: " + approvalId
                    + ", Amount: " + invoice.getAmount();
            if (pmUserId != null && !pmUserId.isBlank()) {
                pushCentral(
                        "INVOICE_SUBMITTED",
                        submitMsg,
                        vendorId,                                  // fromUserId — vendor
                        "PROJECT_MANAGER", pmUserId,               // toUserId — project's PM
                        invoice.getInvoiceId());
            }
            if (vendorId != null && !vendorId.isBlank()) {
                pushCentral(
                        "INVOICE_SUBMITTED",
                        submitMsg,
                        vendorId,                                  // fromUserId — self
                        "VENDOR", vendorId,                        // toUserId — vendor (echo)
                        invoice.getInvoiceId());
            }
        } catch (Exception e) {
            // Revert on failure so vendor can retry. Keep the generated approvalId so
            // future retries advance to a new ID instead of reusing the same one.
            saved.setStatus(InvoiceStatus.PENDING);
            invoiceRepository.save(saved);
            log.error("Failed to submit invoice approval to PM: {}", e.getMessage());
            throw new RuntimeException("Failed to submit approval to PM module: " + e.getMessage());
        }

        return toDTO(saved);
    }

    @Override
    public InvoiceStatus getInvoiceStatus(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        return invoice.getStatus();
    }

    @Override
    public InvoiceResponse updateInvoiceApprovalStatus(String id, String approvedBy) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        if (invoice.getStatus() != InvoiceStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED invoices can be approved. Current status: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setApprovedBy(approvedBy);
        invoice.setApprovedOn(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);
        return toDTO(saved);
    }

    @Override
    public InvoiceResponse updateInvoiceRejectionStatus(String id, String rejectedBy, String rejectionReason) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        if (invoice.getStatus() != InvoiceStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED invoices can be rejected. Current status: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setRejectedBy(rejectedBy);
        invoice.setRejectionReason(rejectionReason);
        invoice.setRejectedOn(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);
        return toDTO(saved);
    }

    @Override
    public InvoiceResponse updateInvoiceApprovalStatusByApprovalId(String approvalId, String approvedBy) {
        Invoice invoice = invoiceRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new RuntimeException("Invoice not found for approvalId: " + approvalId));
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setApprovedBy(approvedBy);
        invoice.setApprovedOn(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        // --- Push INVOICE_APPROVED to the vendor ---
        String vendorId = resolveVendorIdForNotification(saved);
        if (vendorId != null && !vendorId.isBlank()) {
            pushCentral(
                    "INVOICE_APPROVED",
                    String.format("Invoice %s for contract %s APPROVED by %s (approvalId=%s).",
                            invoice.getInvoiceId(), invoice.getContractId(), approvedBy, approvalId),
                    approvedBy,                                    // fromUserId — PM approver
                    "VENDOR", vendorId,                            // toUserId — the vendor
                    invoice.getInvoiceId());
        }

        log.info("Invoice approved: invoiceId={}, approvalId={}, approvedBy={}", invoice.getInvoiceId(), approvalId, approvedBy);
        return toDTO(saved);
    }

    @Override
    public InvoiceResponse updateInvoiceRejectionStatusByApprovalId(String approvalId, String rejectedBy, String rejectionReason) {
        Invoice invoice = invoiceRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new RuntimeException("Invoice not found for approvalId: " + approvalId));
        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setRejectedBy(rejectedBy);
        invoice.setRejectionReason(rejectionReason);
        invoice.setRejectedOn(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        // --- Push INVOICE_REJECTED to the vendor with the reason ---
        String vendorId = resolveVendorIdForNotification(saved);
        if (vendorId != null && !vendorId.isBlank()) {
            pushCentral(
                    "INVOICE_REJECTED",
                    String.format("Invoice %s REJECTED by %s. Reason: %s. Please update and resubmit.",
                            invoice.getInvoiceId(),
                            rejectedBy,
                            rejectionReason != null ? rejectionReason : "Not provided"),
                    rejectedBy,                                    // fromUserId — PM rejecter
                    "VENDOR", vendorId,                            // toUserId — the vendor
                    invoice.getInvoiceId());
        }

        log.info("Invoice rejected: invoiceId={}, approvalId={}, rejectedBy={}, reason={}",
                invoice.getInvoiceId(), approvalId, rejectedBy, rejectionReason);
        return toDTO(saved);
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * toUserId is REQUIRED; if null/blank, the call is skipped (logged) rather
     * than sent as null and rejected by the central service's @NotBlank guard.
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
                    "VENDOR",
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

    /**
     * Looks up the PM userId for a given projectId via the Project Manager
     * service. Returns null on failure — caller skips the PM central push
     * rather than spamming all PMs.
     */
    private String resolvePmUserId(String projectId) {
        if (projectId == null || projectId.isBlank() || "UNKNOWN".equals(projectId)) {
            return null;
        }
        try {
            ProjectDTO project = projectManagerClient.getProjectById(projectId);
            return project != null ? project.projectManager() : null;
        } catch (Exception ex) {
            log.warn("Could not resolve PM userId for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    private InvoiceResponse toDTO(Invoice invoice) {
        InvoiceResponse dto = new InvoiceResponse();
        dto.setInvoiceId(invoice.getInvoiceId());
        dto.setContractId(invoice.getContractId());
        dto.setApprovalId(invoice.getApprovalId());
        dto.setTaskId(invoice.getTaskId());
        dto.setAmount(invoice.getAmount());
        dto.setDate(invoice.getDate());
        dto.setStatus(invoice.getStatus());
        dto.setApprovedBy(invoice.getApprovedBy());
        dto.setRejectedBy(invoice.getRejectedBy());
        dto.setRejectionReason(invoice.getRejectionReason());
        dto.setDescription(invoice.getDescription());
        dto.setSubmittedBy(invoice.getSubmittedBy());
        dto.setSubmittedOn(invoice.getSubmittedOn());
        dto.setApprovedOn(invoice.getApprovedOn());
        dto.setRejectedOn(invoice.getRejectedOn());
        return dto;
    }

    private String resolveLatestApprovalIdBase(Invoice invoice, String repositoryMaxApprovalId) {
        String bestApprovalId = repositoryMaxApprovalId;
        bestApprovalId = pickLaterApprovalId(bestApprovalId, invoice.getApprovalId());

        List<RevisedUpdate> revisions = revisedUpdateRepository
                .findByOriginalEntityIdAndEntityTypeOrderByCreatedAtDesc(invoice.getInvoiceId(), EntityType.INVOICE);

        for (RevisedUpdate revision : revisions) {
            String recoveredApprovalId = extractApprovalIdFromRevision(revision);
            bestApprovalId = pickLaterApprovalId(bestApprovalId, recoveredApprovalId);
            if (bestApprovalId != null) {
                break;
            }
        }

        return bestApprovalId;
    }

    private String resolveGlobalMaxApprovalId() {
        String invoiceMaxApprovalId = invoiceRepository.findTopByApprovalIdStartingWithOrderByApprovalIdDesc("APRVN")
                .map(Invoice::getApprovalId).orElse(null);
        String documentMaxApprovalId = documentRepository.findTopByApprovalIdStartingWithOrderByApprovalIdDesc("APRVN")
                .map(com.buildsmart.vendor.entity.Document::getApprovalId).orElse(null);
        return pickLaterApprovalId(invoiceMaxApprovalId, documentMaxApprovalId);
    }

    private String extractApprovalIdFromRevision(RevisedUpdate revision) {
        try {
            Invoice previousInvoice = objectMapper.readValue(revision.getPreviousData(), Invoice.class);
            return previousInvoice.getApprovalId();
        } catch (Exception e) {
            log.warn("Could not recover approvalId from revision {}: {}", revision.getId(), e.getMessage());
            return null;
        }
    }

    private String pickLaterApprovalId(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return extractApprovalSequence(second) > extractApprovalSequence(first) ? second : first;
    }

    private int extractApprovalSequence(String approvalId) {
        if (approvalId == null || approvalId.length() < 3) {
            return 0;
        }
        try {
            return Integer.parseInt(approvalId.substring(approvalId.length() - 3));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Item #6: enforce that the cumulative non-rejected billed amount for a
     * contract never exceeds the contract's value.
     */
    private void enforceContractValueCap(Contract parentContract,
                                         java.math.BigDecimal thisInvoiceAmount,
                                         String excludedInvoiceId) {
        if (parentContract == null) {
            log.warn("Cannot enforce contract-value cap: contract not found for amount check; skipping.");
            return;
        }
        if (parentContract.getValue() == null) {
            log.warn("Cannot enforce contract-value cap: contract {} has null value; skipping.",
                    parentContract.getContractId());
            return;
        }

        java.math.BigDecimal newAmount = (thisInvoiceAmount != null)
                ? thisInvoiceAmount : java.math.BigDecimal.ZERO;

        // Sum amounts of all non-rejected existing invoices for this contract,
        // skipping the one being updated (if any). Use BigDecimal arithmetic
        // throughout to avoid floating-point drift.
        java.util.List<Invoice> siblings = invoiceRepository.findByContractId(parentContract.getContractId());
        java.math.BigDecimal alreadyBilled = java.math.BigDecimal.ZERO;
        for (Invoice sibling : siblings) {
            if (sibling.getStatus() == InvoiceStatus.REJECTED) {
                continue;
            }
            if (excludedInvoiceId != null && excludedInvoiceId.equals(sibling.getInvoiceId())) {
                continue;
            }
            if (sibling.getAmount() != null) {
                alreadyBilled = alreadyBilled.add(sibling.getAmount());
            }
        }

        java.math.BigDecimal projectedTotal = alreadyBilled.add(newAmount);

        // Use compareTo so 100 and 100.00 compare equal regardless of scale.
        if (projectedTotal.compareTo(parentContract.getValue()) > 0) {
            throw new com.buildsmart.vendor.exception.CustomExceptions
                    .InvoiceExceedsContractValueException(
                    parentContract.getContractId(),
                    parentContract.getValue(),
                    alreadyBilled,
                    newAmount);
        }

        log.debug("Amount cap OK: contract={}, value={}, alreadyBilled={}, thisInvoice={}, projected={}",
                parentContract.getContractId(), parentContract.getValue(),
                alreadyBilled, newAmount, projectedTotal);
    }

    /**
     * Resolves the vendor's IAM userId (e.g. BSVM002) for a given invoice by
     * walking invoice → contract → vendorId. The Contract's vendorId is the
     * JWT-stamped userId (Item 4), which matches the same key used by the
     * PM-pushed TASK_ASSIGNED notifications. Storing every notification under
     * this consistent key is what makes them visible to the vendor when they
     * query the central notification-service.
     *
     * Falls back to invoice.submittedBy if the contract can't be resolved.
     */
    private String resolveVendorIdForNotification(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        if (invoice.getContractId() != null) {
            Contract contract = contractRepository.findById(invoice.getContractId()).orElse(null);
            if (contract != null && contract.getVendorId() != null && !contract.getVendorId().isBlank()) {
                return contract.getVendorId();
            }
        }
        log.warn("Could not resolve vendor userId for invoice {}; falling back to submittedBy",
                invoice.getInvoiceId());
        return invoice.getSubmittedBy();
    }
}