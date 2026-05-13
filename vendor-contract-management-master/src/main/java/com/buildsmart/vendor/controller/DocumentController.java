package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.DocumentResponse;
import com.buildsmart.vendor.dto.request.DocumentRequest;
import com.buildsmart.vendor.enums.DocumentStatus;
import com.buildsmart.vendor.enums.DocumentType;
import com.buildsmart.vendor.exception.CustomExceptions.UnauthorizedException;
import com.buildsmart.vendor.security.AuthenticatedUserResolver;
import com.buildsmart.vendor.service.DocumentService;
import com.buildsmart.vendor.client.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Document", description = "Document management APIs")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    private com.buildsmart.vendor.service.ApprovalSyncService approvalSyncService;

    @Operation(summary = "Get all documents", description = "Retrieves a paginated list of all documents")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved all documents")
    @GetMapping
    public Page<DocumentResponse> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "documentId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return documentService.getAllDocuments(pageable);
    }

    @Operation(summary = "Get document by ID", description = "Retrieves a document by its unique ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "400", description = "Invalid document ID format"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DOCUMENT, id, "documentId");
        DocumentResponse document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(document);
    }

    @Operation(summary = "Get documents by vendor ID", description = "Retrieves all documents for a specific vendor")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved documents")
    @GetMapping("/vendor/{vendorId}")
    public List<DocumentResponse> getDocumentsByVendorId(@PathVariable String vendorId) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.VENDOR, vendorId);
        return documentService.getDocumentsByVendorId(vendorId);
    }

    @Operation(summary = "Get documents by type", description = "Retrieves documents filtered by document type")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved documents")
    @GetMapping("/type/{documentType}")
    public List<DocumentResponse> getDocumentsByType(@PathVariable DocumentType documentType) {
        return documentService.getDocumentsByType(documentType);
    }

    @Operation(summary = "Get documents by vendor ID and type", description = "Retrieves documents for a vendor filtered by type")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved documents")
    @GetMapping("/vendor/{vendorId}/type/{documentType}")
    public List<DocumentResponse> getDocumentsByVendorIdAndType(@PathVariable String vendorId, @PathVariable DocumentType documentType) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.VENDOR, vendorId);
        return documentService.getDocumentsByVendorIdAndType(vendorId, documentType);
    }

    @Operation(summary = "Upload a document",
            description = "Uploads a new document. Both vendorId and uploadedBy are "
                    + "derived from the authenticated vendor's JWT and must NOT be "
                    + "supplied by the client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Vendor identity could not be resolved from JWT")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    public DocumentResponse uploadDocument(
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "taskId", required = false) String taskId,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "contractId", required = false) String contractId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) throws IOException {

        // Items 4 + 8: vendorId and uploadedBy come from the JWT, never from the client.
        UserDto vendor = resolveAuthenticatedVendor(httpRequest);

        DocumentRequest request = new DocumentRequest();
        request.setVendorId(vendor.userId());
        request.setUploadedBy(vendor.name());
        request.setDocumentType(documentType);
        request.setDescription(description);
        request.setTaskId(taskId);
        request.setProjectId(projectId);
        request.setContractId(contractId);
        request.setFilePath("/uploads/" + file.getOriginalFilename());
        request.setDocumentName(file.getOriginalFilename());
        request.setFileSize(file.getSize());
        request.setContentType(file.getContentType());
        return documentService.uploadDocument(request, file);
    }

    @Operation(summary = "Update a document",
            description = "Updates an existing document. The vendorId is derived from "
                    + "the authenticated vendor's JWT, preventing reassignment to "
                    + "another vendor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Vendor identity could not be resolved from JWT")
    })
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    public DocumentResponse updateDocument(
            @PathVariable String id,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "taskId", required = false) String taskId,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "contractId", required = false) String contractId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) throws IOException {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DOCUMENT, id, "documentId");

        UserDto vendor = resolveAuthenticatedVendor(httpRequest);

        DocumentRequest request = new DocumentRequest();
        request.setVendorId(vendor.userId());
        request.setUploadedBy(vendor.name());
        request.setDocumentType(documentType);
        request.setDescription(description);
        request.setTaskId(taskId);
        request.setProjectId(projectId);
        request.setContractId(contractId);

        return documentService.updateDocument(id, request, file);
    }

    @Operation(summary = "Download a document", description = "Downloads an uploaded document by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('VENDOR', 'PROJECT_MANAGER')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DOCUMENT, id, "documentId");
        DocumentResponse documentDTO = documentService.getDocumentById(id);
        Resource resource = documentService.downloadDocument(id);
        String contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + documentDTO.getDocumentName() + "\"")
                .body(resource);
    }

    @Operation(summary = "Delete a document", description = "Deletes a document by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DOCUMENT, id, "documentId");
        documentService.deleteDocument(id);
        return ResponseEntity.ok().build();
    }

     @Operation(summary = "Get documents by status", description = "Retrieves documents filtered by status")
     @ApiResponse(responseCode = "200", description = "Successfully retrieved documents")
     @GetMapping("/status/{status}")
     public List<DocumentResponse> getDocumentsByStatus(@PathVariable DocumentStatus status) {
         return documentService.getDocumentsByStatus(status);
     }

    @Operation(
            summary = "Get documents by project ID",
            description = "Retrieves all vendor documents uploaded for a specific project. " +
                    "Used by the Project Manager to view all vendor documents for a given project.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved documents for the project")
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('VENDOR', 'PROJECT_MANAGER')")
    public ResponseEntity<List<DocumentResponse>> getDocumentsByProjectId(@PathVariable String projectId) {
        List<DocumentResponse> documents = documentService.getDocumentsByProjectId(projectId);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Submit a document for approval", description = "Vendor submits a PENDING document to project manager for review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Document cannot be submitted"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<DocumentResponse> submitDocument(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DOCUMENT, id, "documentId");
        UserDto vendor = resolveAuthenticatedVendor(httpRequest);
        String authorization = httpRequest.getHeader("Authorization");
        DocumentResponse submitted = documentService.submitDocument(id, vendor.name(), authorization);
        return ResponseEntity.ok(submitted);
    }

    @Operation(summary = "Get document submission status", description = "Vendor checks the approval status of a submitted document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('VENDOR', 'PROJECT_MANAGER')")
    public ResponseEntity<java.util.Map<String, String>> getDocumentStatus(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DOCUMENT, id, "documentId");
        // Pull-and-reconcile: PM does not push approve/reject decisions back
        // to the vendor module, so before reporting status we ask PM whether
        // it has acted on this document's approval and mirror the decision.
        DocumentResponse current = documentService.getDocumentById(id);
        if (current != null && current.getStatus() == DocumentStatus.SUBMITTED && current.getApprovalId() != null) {
            approvalSyncService.syncOne(current.getApprovalId(), httpRequest.getHeader("Authorization"));
        }
        DocumentStatus status = documentService.getDocumentStatus(id);
        return ResponseEntity.ok(java.util.Map.of("documentId", id, "status", status.name()));
    }

    /**
     * Resolves the authenticated vendor or throws 401. Used by every endpoint
     * that needs to stamp vendorId or uploadedBy on a document.
     */
    private UserDto resolveAuthenticatedVendor(HttpServletRequest httpRequest) {
        UserDto vendor = authenticatedUserResolver.getCurrentUser(httpRequest);
        if (!authenticatedUserResolver.isUsable(vendor)) {
            throw new UnauthorizedException(
                    "Vendor identity could not be resolved. A valid JWT and reachable IAM service are required.");
        }
        return vendor;
    }
}
