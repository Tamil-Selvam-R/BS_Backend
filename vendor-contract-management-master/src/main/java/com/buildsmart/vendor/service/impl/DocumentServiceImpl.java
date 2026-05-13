package com.buildsmart.vendor.service.impl;

import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.client.dto.ProjectDTO;
import com.buildsmart.vendor.dto.response.DocumentResponse;
import com.buildsmart.vendor.dto.request.DocumentRequest;
import com.buildsmart.vendor.enums.DocumentStatus;
import com.buildsmart.vendor.enums.DocumentType;
import com.buildsmart.vendor.exception.CustomExceptions.DocumentNotFoundException;
import com.buildsmart.vendor.entity.Document;
import com.buildsmart.vendor.entity.Contract;
import com.buildsmart.vendor.repository.DocumentRepository;
import com.buildsmart.vendor.repository.InvoiceRepository;
import com.buildsmart.vendor.service.DocumentService;
import com.buildsmart.vendor.util.IdGeneratorUtil;
import com.buildsmart.vendor.validator.DocumentValidator;
import com.buildsmart.vendor.client.ProjectManagerClient;
import com.buildsmart.vendor.entity.RevisedUpdate;
import com.buildsmart.vendor.enums.EntityType;
import com.buildsmart.vendor.repository.RevisedUpdateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Document lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local VendorNotificationService has been
 * removed — vendors read their notifications from the central service.
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DocumentValidator documentValidator;

    @Autowired
    private ProjectManagerClient projectManagerClient;

    @Autowired
    private RevisedUpdateRepository revisedUpdateRepository;

    @Autowired
    private com.buildsmart.vendor.repository.ContractRepository contractRepository;

    @Autowired
    private com.buildsmart.vendor.validator.ProjectDateValidator projectDateValidator;

    @Autowired
    private com.buildsmart.vendor.service.VendorOwnershipService vendorOwnershipService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Central notification service client — every document event routes to a
     * specific user via toUserId. Fire-and-forget.
     */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    @Override
    public Page<DocumentResponse> getAllDocuments(Pageable pageable) {
        return documentRepository.findAll(pageable).map(this::toDTO);
    }

    @Override
    public DocumentResponse getDocumentById(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return toDTO(document);
    }

    @Override
    public List<DocumentResponse> getDocumentsByVendorId(String vendorId) {
        List<Document> documents = documentRepository.findByVendorId(vendorId);
        List<DocumentResponse> dtoList = new ArrayList<>();
        for (Document document : documents) {
            dtoList.add(toDTO(document));
        }
        return dtoList;
    }

    @Override
    public List<DocumentResponse> getDocumentsByType(DocumentType documentType) {
        List<Document> documents = documentRepository.findByDocumentType(documentType);
        List<DocumentResponse> dtoList = new ArrayList<>();
        for (Document document : documents) {
            dtoList.add(toDTO(document));
        }
        return dtoList;
    }

    @Override
    public List<DocumentResponse> getDocumentsByVendorIdAndType(String vendorId, DocumentType documentType) {
        List<Document> documents = documentRepository.findByVendorIdAndDocumentType(vendorId, documentType);
        List<DocumentResponse> dtoList = new ArrayList<>();
        for (Document document : documents) {
            dtoList.add(toDTO(document));
        }
        return dtoList;
    }

    @Override
    public DocumentResponse uploadDocument(DocumentRequest request, MultipartFile file) {
        try {
            documentValidator.validate(request);

            String resolvedProjectId = request.getProjectId();
            if ((resolvedProjectId == null || resolvedProjectId.isBlank()) && request.getContractId() != null) {
                resolvedProjectId = contractRepository.findById(request.getContractId())
                        .map(Contract::getProjectId)
                        .orElse(null);
            }

            if (resolvedProjectId != null && !resolvedProjectId.isBlank()) {
                if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
                    vendorOwnershipService.requireTaskOwnedByVendor(
                            resolvedProjectId, request.getTaskId(), request.getVendorId());
                } else {
                    vendorOwnershipService.requireProjectOwnedByVendor(
                            resolvedProjectId, request.getVendorId());
                }
            }

            if (resolvedProjectId != null && !resolvedProjectId.isBlank()) {
                projectDateValidator.validateDateWithinProject(
                        resolvedProjectId, java.time.LocalDate.now(), "Document upload date");
            }

            String lastId = documentRepository.findTopByOrderByDocumentIdDesc()
                    .map(Document::getDocumentId)
                    .orElse(null);

            String uploadDir = "uploads/";
            Path path = Paths.get(uploadDir + file.getOriginalFilename()).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            request.setContentType(file.getContentType());
            request.setDocumentName(file.getOriginalFilename());
            request.setFilePath(path.toString());

            Document document = new Document();
            document.setDocumentId(IdGeneratorUtil.nextDocumentId(lastId));
            document.setVendorId(request.getVendorId());
            document.setDocumentName(request.getDocumentName());
            document.setDocumentType(request.getDocumentType());
            document.setContentType(request.getContentType());
            document.setFilePath(request.getFilePath());
            document.setFileSize(request.getFileSize());
            document.setUploadedBy(request.getUploadedBy());
            document.setDescription(request.getDescription());
            document.setUploadedAt(LocalDateTime.now());
            document.setStatus(DocumentStatus.PENDING);
            document.setContractId(request.getContractId());

            if ((request.getTaskId() == null || request.getTaskId().isBlank()) && request.getContractId() != null) {
                contractRepository.findById(request.getContractId())
                        .ifPresent(contract -> document.setTaskId(contract.getTaskId()));
            } else {
                document.setTaskId(request.getTaskId());
            }
            document.setProjectId(request.getProjectId());

            Document saved = documentRepository.save(document);
            return toDTO(saved);

        } catch (com.buildsmart.vendor.exception.CustomExceptions.ResourceNotFoundException
                 | com.buildsmart.vendor.exception.CustomExceptions.ValidationException
                 | com.buildsmart.vendor.exception.CustomExceptions.InvalidIdFormatException
                 | com.buildsmart.vendor.exception.CustomExceptions.InvalidDateRangeException domain) {
            throw domain;
        } catch (Exception e) {
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }
    }

    @Override
    public DocumentResponse updateDocument(String id, DocumentRequest request, MultipartFile file) {
        try {
            Document existing = documentRepository.findById(id)
                    .orElseThrow(() -> new DocumentNotFoundException(id));

            if (file != null && !file.isEmpty()) {
                request.setDocumentName(file.getOriginalFilename());
                request.setFilePath("uploads/" + file.getOriginalFilename());
                request.setFileSize(file.getSize());
                request.setContentType(file.getContentType());
            } else {
                request.setDocumentName(existing.getDocumentName());
                request.setFilePath(existing.getFilePath());
                request.setFileSize(existing.getFileSize());
                request.setContentType(existing.getContentType());
            }

            documentValidator.validate(request);

            String resolvedProjectId = request.getProjectId();
            if ((resolvedProjectId == null || resolvedProjectId.isBlank()) && request.getContractId() != null) {
                resolvedProjectId = contractRepository.findById(request.getContractId())
                        .map(Contract::getProjectId)
                        .orElse(null);
            }
            if (resolvedProjectId != null && !resolvedProjectId.isBlank()) {
                if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
                    vendorOwnershipService.requireTaskOwnedByVendor(
                            resolvedProjectId, request.getTaskId(), request.getVendorId());
                } else {
                    vendorOwnershipService.requireProjectOwnedByVendor(
                            resolvedProjectId, request.getVendorId());
                }
            }

            if (file != null && !file.isEmpty()) {
                Path path = Paths.get("uploads/" + file.getOriginalFilename()).toAbsolutePath().normalize();
                Files.createDirectories(path.getParent());
                Files.copy(file.getInputStream(), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                existing.setFilePath(path.toString());
                existing.setDocumentName(file.getOriginalFilename());
                existing.setFileSize(file.getSize());
                existing.setContentType(file.getContentType());
            }

            existing.setDocumentType(request.getDocumentType());
            existing.setDescription(request.getDescription());
            existing.setContractId(request.getContractId());

            if ((request.getTaskId() == null || request.getTaskId().isBlank()) && request.getContractId() != null) {
                contractRepository.findById(request.getContractId())
                        .ifPresent(contract -> existing.setTaskId(contract.getTaskId()));
            } else {
                existing.setTaskId(request.getTaskId());
            }
            existing.setProjectId(request.getProjectId());

            // If updating a REJECTED document, record the revision and reset to PENDING
            if (existing.getStatus() == DocumentStatus.REJECTED) {
                String previousRejectionReason = existing.getRejectionReason();

                try {
                    RevisedUpdate revisedUpdate = new RevisedUpdate();
                    revisedUpdate.setEntityType(EntityType.DOCUMENT);
                    revisedUpdate.setOriginalEntityId(existing.getDocumentId());
                    revisedUpdate.setRejectionReason(previousRejectionReason);
                    revisedUpdate.setPreviousData(objectMapper.writeValueAsString(existing));
                    revisedUpdate.setUpdatedData(objectMapper.writeValueAsString(request));
                    revisedUpdateRepository.save(revisedUpdate);
                } catch (Exception e) {
                    log.warn("Could not save revision record for document {}: {}", id, e.getMessage());
                }

                existing.setStatus(DocumentStatus.PENDING);
                existing.setRejectionReason(null);
                existing.setRejectedBy(null);

                Document updated = documentRepository.save(existing);

                try {
                    projectManagerClient.notifyDocumentRejected(
                            updated.getDocumentId(),
                            updated.getApprovalId(),
                            updated.getRejectedBy(),
                            previousRejectionReason
                    );
                } catch (Exception e) {
                    log.warn("Failed to notify PM module about document rejection: {}", e.getMessage());
                }

                // --- Push APPROVAL_REJECTED to the vendor ---
                // The vendor needs to know to update and resubmit.
                String rejectMsg = String.format(
                        "Document %s was REJECTED. Reason: %s. Please update and resubmit.",
                        updated.getDocumentId(), previousRejectionReason);
                pushCentral("APPROVAL_REJECTED", rejectMsg,
                        null,                                      // fromUserId — system-generated reset
                        "VENDOR", updated.getVendorId(),           // toUserId — the vendor
                        updated.getDocumentId());

                return toDTO(updated);
            }

            Document updated = documentRepository.save(existing);
            return toDTO(updated);

        } catch (com.buildsmart.vendor.exception.CustomExceptions.ResourceNotFoundException
                 | com.buildsmart.vendor.exception.CustomExceptions.ValidationException
                 | com.buildsmart.vendor.exception.CustomExceptions.InvalidIdFormatException
                 | com.buildsmart.vendor.exception.CustomExceptions.InvalidDateRangeException domain) {
            throw domain;
        } catch (Exception e) {
            throw new RuntimeException("Document update failed: " + e.getMessage());
        }
    }

    @Override
    public Resource downloadDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        try {
            Path filePath = Paths.get(document.getFilePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("File not found or not readable: " + document.getFilePath());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error reading file: " + document.getFilePath(), e);
        }
    }

    @Override
    public void deleteDocument(String id) {
        if (!documentRepository.existsById(id)) {
            throw new DocumentNotFoundException(id);
        }
        documentRepository.deleteById(id);
    }

    @Override
    public DocumentResponse submitDocument(String id, String submittedBy, String authorization) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (document.getStatus() != DocumentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING documents can be submitted. Current status: " + document.getStatus()
                            + ". If previously rejected, update the document first to reset it to PENDING.");
        }

        String approvalId = IdGeneratorUtil.nextApprovalId(resolveLatestApprovalIdBase(document, resolveGlobalMaxApprovalId()));

        document.setStatus(DocumentStatus.SUBMITTED);
        document.setUploadedBy(submittedBy);
        document.setApprovalId(approvalId);
        Document saved = documentRepository.save(document);

        String projectId = saved.getProjectId();
        String taskId = saved.getTaskId();
        if ((projectId == null || projectId.isBlank()) && saved.getContractId() != null) {
            Contract contract = contractRepository.findById(saved.getContractId()).orElse(null);
            if (contract != null) {
                if (projectId == null || projectId.isBlank()) projectId = contract.getProjectId();
                if (taskId == null || taskId.isBlank()) taskId = contract.getTaskId();
            }
        }
        if (projectId == null || projectId.isBlank()) projectId = "UNKNOWN";
        if (taskId == null || taskId.isBlank()) taskId = "UNKNOWN_TASK";

        // Resolve the project's PM userId so we can route the central notification.
        String pmUserId = resolvePmUserId(saved.getProjectId() != null ? saved.getProjectId() : projectId);

        try {
            com.buildsmart.vendor.client.dto.PMClientApprovalRequest pmRequest =
                    new com.buildsmart.vendor.client.dto.PMClientApprovalRequest();
            pmRequest.setApprovalId(approvalId);
            pmRequest.setProjectId(projectId);
            pmRequest.setTaskId(taskId);
            pmRequest.setApprovalType("VENDOR");
            String docName = saved.getDocumentName() != null ? saved.getDocumentName() : saved.getDocumentId();
            String docTypeStr = saved.getDocumentType() != null ? saved.getDocumentType().name() : "DOCUMENT";
            pmRequest.setDescription("Document submitted for approval by " + submittedBy
                    + ". Document ID: " + saved.getDocumentId()
                    + ". Name: " + docName
                    + ". Type: " + docTypeStr
                    + ". " + (saved.getDescription() != null ? saved.getDescription() : ""));
            pmRequest.setAmount(0.0);
            pmRequest.setRequestedBy(submittedBy);
            pmRequest.setRequestedByDepartment("VENDOR");

            projectManagerClient.createApprovalRequest(authorization, pmRequest);
            log.info("Approval request sent to PM for documentId={}, approvalId={}", saved.getDocumentId(), approvalId);

            try {
                projectManagerClient.notifyDocumentSubmitted(
                        saved.getDocumentId(),
                        saved.getContractId(),
                        saved.getTaskId(),
                        saved.getDocumentName(),
                        saved.getDocumentType() != null ? saved.getDocumentType().name() : null,
                        saved.getUploadedBy()
                );
            } catch (Exception notifyEx) {
                log.warn("PM approval created but document-submitted notification failed for documentId={}: {}",
                        saved.getDocumentId(), notifyEx.getMessage());
            }

            // --- Push DOCUMENT_SUBMITTED to PM and to vendor (echo) ---
            String submitMsg = String.format(
                    "Document %s (%s) submitted for approval. Approval ID: %s",
                    saved.getDocumentId(), docName, approvalId);
            if (pmUserId != null) {
                pushCentral("DOCUMENT_SUBMITTED", submitMsg,
                        saved.getVendorId(),                       // fromUserId — vendor
                        "PROJECT_MANAGER", pmUserId,               // toUserId — project's PM
                        saved.getDocumentId());
            }
            pushCentral("DOCUMENT_SUBMITTED", submitMsg,
                    saved.getVendorId(),                           // fromUserId — vendor (echo)
                    "VENDOR", saved.getVendorId(),                 // toUserId — vendor themselves
                    saved.getDocumentId());

        } catch (Exception e) {
            saved.setStatus(DocumentStatus.PENDING);
            documentRepository.save(saved);
            log.error("Failed to submit document approval to PM: {}", e.getMessage());
            throw new RuntimeException("Failed to submit approval to PM module: " + e.getMessage());
        }

        return toDTO(saved);
    }

    @Override
    public List<DocumentResponse> getDocumentsByStatus(DocumentStatus status) {
        List<Document> documents = documentRepository.findByStatus(status);
        List<DocumentResponse> dtoList = new ArrayList<>();
        for (Document document : documents) {
            dtoList.add(toDTO(document));
        }
        return dtoList;
    }

    @Override
    public List<DocumentResponse> getDocumentsByProjectId(String projectId) {
        List<Document> documents = documentRepository.findByProjectId(projectId);
        List<DocumentResponse> dtoList = new ArrayList<>();
        for (Document document : documents) {
            dtoList.add(toDTO(document));
        }
        return dtoList;
    }

    @Override
    public DocumentStatus getDocumentStatus(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return document.getStatus();
    }

    @Override
    public DocumentResponse updateDocumentApprovalStatus(String id, String approvedBy) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (document.getStatus() != DocumentStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED documents can be approved. Current status: " + document.getStatus());
        }
        document.setStatus(DocumentStatus.APPROVED);
        document.setApprovedBy(approvedBy);
        Document saved = documentRepository.save(document);

        try {
            projectManagerClient.notifyDocumentApproved(
                    saved.getDocumentId(),
                    saved.getApprovalId(),
                    approvedBy
            );
        } catch (Exception e) {
            log.warn("Failed to notify PM module about document approval: {}", e.getMessage());
        }

        // --- Push APPROVAL_GRANTED to the vendor (the one who submitted) ---
        String approveMsg = String.format(
                "Document %s has been APPROVED by %s.",
                saved.getDocumentId(), approvedBy);
        pushCentral("APPROVAL_GRANTED", approveMsg,
                approvedBy,                                        // fromUserId — approver (PM)
                "VENDOR", saved.getVendorId(),                     // toUserId — the vendor
                saved.getDocumentId());

        return toDTO(saved);
    }

    @Override
    public DocumentResponse updateDocumentRejectionStatus(String id, String rejectedBy, String rejectionReason) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (document.getStatus() != DocumentStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED documents can be rejected. Current status: " + document.getStatus());
        }
        document.setStatus(DocumentStatus.REJECTED);
        document.setRejectedBy(rejectedBy);
        document.setRejectionReason(rejectionReason);
        Document saved = documentRepository.save(document);

        // --- Push APPROVAL_REJECTED to the vendor ---
        String rejectMsg = String.format(
                "Document %s was REJECTED by %s. Reason: %s. Please update and resubmit.",
                saved.getDocumentId(), rejectedBy, rejectionReason);
        pushCentral("APPROVAL_REJECTED", rejectMsg,
                rejectedBy,                                        // fromUserId — rejecter
                "VENDOR", saved.getVendorId(),                     // toUserId — the vendor
                saved.getDocumentId());

        return toDTO(saved);
    }

    @Override
    public DocumentResponse updateDocumentApprovalStatusByApprovalId(String approvalId, String approvedBy) {
        Document document = documentRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new RuntimeException("Document not found for approvalId: " + approvalId));
        document.setStatus(DocumentStatus.APPROVED);
        document.setApprovedBy(approvedBy);
        Document saved = documentRepository.save(document);

        log.info("Document approved: documentId={}, approvalId={}, approvedBy={}",
                document.getDocumentId(), approvalId, approvedBy);

        // --- Push APPROVAL_GRANTED to the vendor ---
        String approveMsg = String.format(
                "Your document %s (%s) has been APPROVED by %s. Approval ID: %s",
                saved.getDocumentId(), saved.getDocumentName(), approvedBy, approvalId);
        pushCentral("APPROVAL_GRANTED", approveMsg,
                approvedBy,
                "VENDOR", saved.getVendorId(),
                saved.getDocumentId());

        return toDTO(saved);
    }

    @Override
    public DocumentResponse updateDocumentRejectionStatusByApprovalId(String approvalId, String rejectedBy, String rejectionReason) {
        Document document = documentRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new RuntimeException("Document not found for approvalId: " + approvalId));
        document.setStatus(DocumentStatus.REJECTED);
        document.setRejectedBy(rejectedBy);
        document.setRejectionReason(rejectionReason);
        Document saved = documentRepository.save(document);

        log.info("Document rejected: documentId={}, approvalId={}, rejectedBy={}, reason={}",
                document.getDocumentId(), approvalId, rejectedBy, rejectionReason);

        // --- Push APPROVAL_REJECTED to the vendor ---
        String rejectMsg = String.format(
                "Your document %s (%s) was REJECTED by %s. Reason: %s. Please update and resubmit.",
                saved.getDocumentId(), saved.getDocumentName(), rejectedBy, rejectionReason);
        pushCentral("APPROVAL_REJECTED", rejectMsg,
                rejectedBy,
                "VENDOR", saved.getVendorId(),
                saved.getDocumentId());

        return toDTO(saved);
    }

    private DocumentResponse toDTO(Document document) {
        DocumentResponse dto = new DocumentResponse();
        dto.setDocumentId(document.getDocumentId());
        dto.setVendorId(document.getVendorId());
        dto.setApprovalId(document.getApprovalId());
        dto.setTaskId(document.getTaskId());
        dto.setProjectId(document.getProjectId());
        dto.setContractId(document.getContractId());
        dto.setDocumentName(document.getDocumentName());
        dto.setDocumentType(document.getDocumentType());
        dto.setFilePath(document.getFilePath());
        dto.setFileSize(document.getFileSize());
        dto.setDescription(document.getDescription());
        dto.setUploadedBy(document.getUploadedBy());
        dto.setUploadedAt(document.getUploadedAt());
        dto.setStatus(document.getStatus());
        dto.setApprovedBy(document.getApprovedBy());
        dto.setRejectedBy(document.getRejectedBy());
        dto.setRejectionReason(document.getRejectionReason());
        return dto;
    }

    private String resolveLatestApprovalIdBase(Document document, String repositoryMaxApprovalId) {
        String bestApprovalId = repositoryMaxApprovalId;
        bestApprovalId = pickLaterApprovalId(bestApprovalId, document.getApprovalId());

        List<RevisedUpdate> revisions = revisedUpdateRepository
                .findByOriginalEntityIdAndEntityTypeOrderByCreatedAtDesc(document.getDocumentId(), EntityType.DOCUMENT);

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
        String documentMaxApprovalId = documentRepository.findTopByApprovalIdStartingWithOrderByApprovalIdDesc("APRVN")
                .map(Document::getApprovalId).orElse(null);
        String invoiceMaxApprovalId = invoiceRepository.findTopByApprovalIdStartingWithOrderByApprovalIdDesc("APRVN")
                .map(com.buildsmart.vendor.entity.Invoice::getApprovalId).orElse(null);
        return pickLaterApprovalId(documentMaxApprovalId, invoiceMaxApprovalId);
    }

    private String extractApprovalIdFromRevision(RevisedUpdate revision) {
        try {
            Document previousDocument = objectMapper.readValue(revision.getPreviousData(), Document.class);
            return previousDocument.getApprovalId();
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
     * Looks up the PM userId for a given projectId via the Project Manager
     * service. Returns null if PM is unreachable — caller falls back to
     * skipping the central PM notification (rather than spamming all PMs).
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
}