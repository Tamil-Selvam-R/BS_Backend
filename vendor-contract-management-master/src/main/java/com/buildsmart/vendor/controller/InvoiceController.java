package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.InvoiceResponse;
import com.buildsmart.vendor.dto.request.InvoiceRequest;
import com.buildsmart.vendor.enums.InvoiceStatus;
import com.buildsmart.vendor.service.InvoiceService;
import com.buildsmart.vendor.client.IAMServiceClient;
import com.buildsmart.vendor.client.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoice", description = "Invoice management APIs")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private IAMServiceClient iamServiceClient;

    @Autowired
    private com.buildsmart.vendor.service.ApprovalSyncService approvalSyncService;

    @Operation(summary = "Get all invoices", description = "Retrieves a paginated list of all invoices")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved all invoices")
    @GetMapping
    public Page<InvoiceResponse> getAllInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "invoiceId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return invoiceService.getAllInvoices(pageable);
    }

    @Operation(summary = "Get invoice by ID", description = "Retrieves an invoice by its unique ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "400", description = "Invalid invoice ID format"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        if (invoice == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(invoice);
    }

    @Operation(summary = "Get invoices by contract ID", description = "Retrieves invoices for a specific contract")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved invoices")
    @GetMapping("/contract/{contractId}")
    public List<InvoiceResponse> getInvoicesByContractId(@PathVariable String contractId) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, contractId);
        return invoiceService.getInvoicesByContractId(contractId);
    }

     @Operation(summary = "Get invoices by status", description = "Retrieves invoices filtered by status")
     @ApiResponse(responseCode = "200", description = "Successfully retrieved invoices")
     @GetMapping("/status/{status}")
     public List<InvoiceResponse> getInvoicesByStatus(@PathVariable InvoiceStatus status) {
         return invoiceService.getInvoicesByStatus(status);
     }

    @Operation(summary = "Create a new invoice",
            description = "Creates a new invoice record. The submittedBy field is derived "
                    + "from the authenticated vendor's JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Vendor identity could not be resolved from JWT")
    })
    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public InvoiceResponse createInvoice(@RequestBody InvoiceRequest request, HttpServletRequest httpRequest) {
        String submittedBy = getLoggedInUserName(httpRequest);
        return invoiceService.createInvoice(request, submittedBy);
    }

    @Operation(summary = "Update an invoice", description = "Updates an existing invoice by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<InvoiceResponse> updateInvoice(@PathVariable String id, @RequestBody InvoiceRequest request) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");
        InvoiceResponse updated = invoiceService.updateInvoice(id, request);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete an invoice", description = "Deletes an invoice by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");
        invoiceService.deleteInvoice(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Submit an invoice for approval", description = "Vendor submits a PENDING invoice to project manager for review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Invoice cannot be submitted"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<InvoiceResponse> submitInvoice(
            @PathVariable String id,
            HttpServletRequest request) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");
        String submittedBy = getLoggedInUserName(request);
        String authorization = request.getHeader("Authorization");
        InvoiceResponse submitted = invoiceService.submitInvoice(id, submittedBy, authorization);
        return ResponseEntity.ok(submitted);
    }

    @Operation(summary = "Get invoice submission status", description = "Vendor checks the approval status of a submitted invoice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('VENDOR', 'PROJECT_MANAGER')")
    public ResponseEntity<java.util.Map<String, String>> getInvoiceStatus(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");
        // Pull-and-reconcile: if PM has acted on this invoice's approval since
        // submission, mirror that decision into vendor state before reporting.
        InvoiceResponse current = invoiceService.getInvoiceById(id);
        if (current != null && current.getStatus() == InvoiceStatus.SUBMITTED && current.getApprovalId() != null) {
            approvalSyncService.syncOne(current.getApprovalId(), httpRequest.getHeader("Authorization"));
        }
        InvoiceStatus status = invoiceService.getInvoiceStatus(id);
        return ResponseEntity.ok(java.util.Map.of("invoiceId", id, "status", status.name()));
    }

    private String getLoggedInUserName(HttpServletRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String token = request.getHeader("Authorization");
        UserDto user = iamServiceClient.getUserByEmail(email, token);
        return user.name();
    }
}
