package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.PMClientApprovalRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Feign client for communicating with the Project Manager service via Eureka discovery.
 * The PM service runs at port 8086 with context-path /api.
 * All endpoints here must be prefixed with /api to match the PM module's context path.
 */
@FeignClient(
        name = "project-service",
        contextId = "vendorProjectManagerClient",
        fallbackFactory = ProjectManagerClientFallbackFactory.class
)
public interface ProjectManagerClient {
    /**
     * Notify PM that a document has been submitted by the vendor.
     * PM endpoint: POST /api/notifications/document-submitted
     */
    @PostMapping("/api/notifications/document-submitted")
    void notifyDocumentSubmitted(
        @RequestParam("documentId") String documentId,
        @RequestParam("contractId") String contractId,
        @RequestParam(value = "taskId", required = false) String taskId,
        @RequestParam("documentName") String documentName,
        @RequestParam("documentType") String documentType,
        @RequestParam("submittedBy") String submittedBy
    );

    /**
     * Notify PM that a document has been approved.
     * PM endpoint: POST /api/notifications/document-approved
     */
    @PostMapping("/api/notifications/document-approved")
    void notifyDocumentApproved(
        @RequestParam("documentId") String documentId,
        @RequestParam("approvalId") String approvalId,
        @RequestParam("approvedBy") String approvedBy
    );

    /**
     * Notify PM that a document has been rejected.
     * PM endpoint: POST /api/notifications/document-rejected
     */
    @PostMapping("/api/notifications/document-rejected")
    void notifyDocumentRejected(
        @RequestParam("documentId") String documentId,
        @RequestParam("approvalId") String approvalId,
        @RequestParam("rejectedBy") String rejectedBy,
        @RequestParam("rejectionReason") String rejectionReason
    );

    /**
     * Vendor submits an invoice/document approval request to PM.
     * PM endpoint: POST /api/approvals
     */
    @PostMapping("/api/approvals")
    Object createApprovalRequest(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @RequestBody PMClientApprovalRequest request);

    /**
     * Get all pending approvals from PM.
     * PM endpoint: GET /api/approvals/pending
     */
    @GetMapping("/api/approvals/pending")
    List<Object> getPendingApprovals();

    /**
     * Typed view of PM's full approvals list. Used by the vendor module to
     * reconcile invoice/document statuses after PM approves or rejects them
     * (since PM does not push back into the vendor module).
     * PM endpoint: GET /api/approvals
     */
    @GetMapping("/api/approvals")
    List<com.buildsmart.vendor.client.dto.PMApprovalDTO> getAllApprovalsTyped(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    /**
     * Get approvals by project from PM.
     * PM endpoint: GET /api/approvals/project/{projectId}
     */
    @GetMapping("/api/approvals/project/{projectId}")
    List<Object> getApprovalsByProject(@PathVariable("projectId") String projectId);

    /**
     * Get approval statistics from PM.
     * PM endpoint: GET /api/approvals/stats
     */
    @GetMapping("/api/approvals/stats")
    Object getApprovalStats();

    /**
     * Get tasks for a specific project from PM.
     * PM endpoint: GET /api/projects/{projectId}/tasks
     */
    @GetMapping("/api/projects/{projectId}/tasks")
    List<Object> getProjectTasks(@PathVariable("projectId") String projectId);

    /**
     * Same as {@link #getProjectTasks(String)} but with a typed return so the
     * vendor module can inspect {@code assignedTo} for ownership checks
     * (Item 5). Hits the same PM endpoint; just deserialises into a typed DTO.
     */
    @GetMapping("/api/projects/{projectId}/tasks")
    List<com.buildsmart.vendor.client.dto.PMTaskDTO> getProjectTasksTyped(@PathVariable("projectId") String projectId);

    /**
     * Notify PM that a contract has been created with the given task ID.
     * PM endpoint: POST /api/notifications/contract-created
     */
    @PostMapping("/api/notifications/contract-created")
    void notifyContractCreated(
            @RequestParam("contractId") String contractId,
            @RequestParam("vendorId") String vendorId,
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "taskId", required = false) String taskId
    );

    /**
     * Fetch a project's metadata (including startDate / endDate) from PM.
     * Used by the vendor module to validate that contracts, invoices, deliveries,
     * and documents fall within the project window.
     * PM endpoint: GET /api/projects/{projectId}
     */
    @GetMapping("/api/projects/{projectId}")
    com.buildsmart.vendor.client.dto.ProjectDTO getProjectById(@PathVariable("projectId") String projectId);

    /**
     * Notify PM that a task has been assigned to a vendor.
     * PM endpoint: POST /api/notifications/task-assigned
     */
    @PostMapping("/api/notifications/task-assigned")
    void notifyTaskAssigned(
        @RequestParam("vendorId") String vendorId,
        @RequestParam("taskId") String taskId,
        @RequestParam("projectId") String projectId,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "plannedStart", required = false) String plannedStart,
        @RequestParam(value = "plannedEnd", required = false) String plannedEnd,
        @RequestParam(value = "assignedDepartment", required = false) String assignedDepartment
    );

    /**
     * Notify PM that an invoice has been submitted by the vendor.
     * PM endpoint: POST /api/notifications/invoice-submitted
     */
    @PostMapping("/api/notifications/invoice-submitted")
    void notifyInvoiceSubmitted(
        @RequestParam("invoiceId") String invoiceId,
        @RequestParam("contractId") String contractId,
        @RequestParam(value = "taskId", required = false) String taskId,
        @RequestParam("amount") java.math.BigDecimal amount,
        @RequestParam("date") java.time.LocalDate date,
        @RequestParam("submittedBy") String submittedBy
    );
}
