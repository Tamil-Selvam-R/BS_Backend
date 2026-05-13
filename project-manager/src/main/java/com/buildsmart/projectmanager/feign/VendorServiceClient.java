package com.buildsmart.projectmanager.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@FeignClient(
    name = "vendor-service",
    configuration = FeignClientConfig.class
)
public interface VendorServiceClient {

    @PutMapping("/api/vendor-integration/approvals/{approvalId}/status")
    ResponseEntity<String> updateApprovalStatus(
        @PathVariable("approvalId") String approvalId,
        @RequestParam("status") String status,
        @RequestParam("rejectedBy") String actionBy,
        @RequestParam(value = "approvedByName", required = false) String approvedByName,
        @RequestParam(value = "rejectionReason", required = false) String rejectionReason
    );

    @PostMapping("/api/vendor-integration/tasks/notify")
    ResponseEntity<String> notifyTaskAssigned(
        @RequestParam("vendorId") String vendorId,
        @RequestParam("taskId") String taskId,
        @RequestParam("projectId") String projectId,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "plannedStart", required = false) String plannedStart,
        @RequestParam(value = "plannedEnd", required = false) String plannedEnd,
        @RequestParam(value = "assignedDepartment", required = false) String assignedDepartment
    );

    @GetMapping("/api/vendor-integration/projects/{projectId}/tasks")
    ResponseEntity<List<Object>> getTasksForProject(@PathVariable("projectId") String projectId);

    /** PM fetches vendor invoices by status (e.g. "SUBMITTED") to see what needs review. */
    @GetMapping("/api/invoices/status/{status}")
    List<InvoiceDto> getInvoicesByStatus(@PathVariable("status") String status);

    /** Minimal invoice DTO — matches vendor InvoiceResponse fields the PM needs. */
    record InvoiceDto(
            String id,
            String contractId,
            String approvalId,
            String taskId,
            Double amount,
            String status,
            String rejectionReason
    ) {}

    /**
     * Lists all vendor documents (paginated). PM uses this to see what vendors have uploaded.
     * Vendor endpoint: GET /api/documents?page=0&size=50
     */
    @GetMapping("/api/documents")
    ResponseEntity<Object> getVendorDocuments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size);

    /**
     * Lists all vendor documents uploaded for a specific project.
     * Vendor endpoint: GET /api/documents/project/{projectId}
     */
    @GetMapping("/api/documents/project/{projectId}")
    List<DocumentDto> getVendorDocumentsByProjectId(
            @PathVariable("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);

    /**
     * Downloads the binary file of a vendor document.
     * Vendor endpoint: GET /api/documents/{id}/download (already allows PROJECT_MANAGER role).
     */
    @GetMapping("/api/documents/{id}/download")
    ResponseEntity<byte[]> downloadVendorDocument(
            @PathVariable("id") String id,
            @RequestHeader("Authorization") String authorization);

    /** Minimal document DTO for PM to list vendor uploads. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DocumentDto(
            String documentId,
            String vendorId,
            String documentName,
            String documentType,
            String description,
            String status,
            String projectId,
            String taskId,
            String contractId,
            String contentType,
            Long fileSize,
            String uploadedBy,
            String uploadedAt
    ) {}
}

