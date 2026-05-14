package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.InvoiceResponse;
import com.buildsmart.vendor.dto.request.InvoiceRequest;
import com.buildsmart.vendor.enums.InvoiceStatus;
import com.buildsmart.vendor.security.AuthenticatedUserResolver;
import com.buildsmart.vendor.service.ContractService;
import com.buildsmart.vendor.service.InvoiceService;
import com.buildsmart.vendor.service.ApprovalSyncService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Invoice REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - VENDOR: sees only invoices whose contractId belongs to their own contracts.
 *     ContractIds are resolved from their JWT vendorId via ContractService.
 *   - ADMIN / PROJECT_MANAGER: unrestricted access to all invoices.
 */
@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoice", description = "Invoice management APIs")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ContractService contractService;

    @Autowired
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    private ApprovalSyncService approvalSyncService;

    // ── GET endpoints — vendor-scoped ────────────────────────────────────────

    @Operation(
            summary = "Get all invoices (paginated)",
            description = "**VENDOR**: returns only invoices for the vendor's own contracts.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: returns all invoices.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved invoices")
    @GetMapping
    public Page<InvoiceResponse> getAllInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "invoiceId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest httpRequest) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        if (isVendor()) {
            String vendorId = resolveVendorId(httpRequest);
            List<String> contractIds = contractService.getContractIdsByVendorId(vendorId);
            return invoiceService.getInvoicesByContractIds(contractIds, pageable);
        }
        return invoiceService.getAllInvoices(pageable);
    }

    @Operation(
            summary = "Get invoice by ID",
            description = "**VENDOR**: returns 403 if the invoice's contract does not belong to the calling vendor.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: unrestricted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "400", description = "Invalid invoice ID format"),
            @ApiResponse(responseCode = "403", description = "Vendor accessing another vendor's invoice"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        if (invoice == null) {
            return ResponseEntity.notFound().build();
        }
        if (isVendor()) {
            assertInvoiceOwnership(httpRequest, invoice.getContractId());
        }
        return ResponseEntity.ok(invoice);
    }

    @Operation(
            summary = "Get invoices by contract ID",
            description = "**VENDOR**: returns 403 if the contract does not belong to the calling vendor.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: unrestricted.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved invoices")
    @GetMapping("/contract/{contractId}")
    public List<InvoiceResponse> getInvoicesByContractId(
            @PathVariable String contractId,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, contractId);
        if (isVendor()) {
            assertInvoiceOwnership(httpRequest, contractId);
        }
        return invoiceService.getInvoicesByContractId(contractId);
    }

    @Operation(
            summary = "Get invoices by status",
            description = "**VENDOR**: returns only their own invoices with the given status.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: returns all invoices with the given status.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved invoices")
    @GetMapping("/status/{status}")
    public List<InvoiceResponse> getInvoicesByStatus(
            @PathVariable InvoiceStatus status,
            HttpServletRequest httpRequest) {

        if (isVendor()) {
            String vendorId = resolveVendorId(httpRequest);
            List<String> contractIds = contractService.getContractIdsByVendorId(vendorId);
            return invoiceService.getInvoicesByContractIdsAndStatus(contractIds, status);
        }
        return invoiceService.getInvoicesByStatus(status);
    }

    @Operation(
            summary = "Get invoice approval status",
            description = "**VENDOR**: returns 403 if the invoice does not belong to the calling vendor.\n\n" +
                    "**PROJECT_MANAGER**: unrestricted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Vendor accessing another vendor's invoice status"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('VENDOR', 'PROJECT_MANAGER')")
    public ResponseEntity<java.util.Map<String, String>> getInvoiceStatus(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.INVOICE, id, "invoiceId");

        InvoiceResponse current = invoiceService.getInvoiceById(id);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        if (isVendor()) {
            assertInvoiceOwnership(httpRequest, current.getContractId());
        }
        // Pull-and-reconcile: sync PM approval decision before reporting status.
        if (current.getStatus() == InvoiceStatus.SUBMITTED && current.getApprovalId() != null) {
            approvalSyncService.syncOne(current.getApprovalId(), httpRequest.getHeader("Authorization"));
        }
        InvoiceStatus status = invoiceService.getInvoiceStatus(id);
        return ResponseEntity.ok(java.util.Map.of("invoiceId", id, "status", status.name()));
    }

    // ── Mutation endpoints (VENDOR role only) ────────────────────────────────

    @Operation(summary = "Create a new invoice",
            description = "Creates a new invoice record. The submittedBy field is derived from the authenticated vendor's JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Vendor identity could not be resolved from JWT")
    })
    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public InvoiceResponse createInvoice(@RequestBody InvoiceRequest request, HttpServletRequest httpRequest) {
        String submittedBy = authenticatedUserResolver.getCurrentUserName(httpRequest);
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
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @PathVariable String id,
            @RequestBody InvoiceRequest request) {
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

    @Operation(summary = "Submit an invoice for approval",
            description = "Vendor submits a PENDING invoice to the Project Manager for review.")
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
        String submittedBy = authenticatedUserResolver.getCurrentUserName(request);
        String authorization = request.getHeader("Authorization");
        InvoiceResponse submitted = invoiceService.submitInvoice(id, submittedBy, authorization);
        return ResponseEntity.ok(submitted);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** True when the current JWT holder has the VENDOR role. */
    private boolean isVendor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));
    }

    /**
     * Resolves the authenticated vendor's IAM userId.
     * Throws 401 if the identity cannot be determined.
     */
    private String resolveVendorId(HttpServletRequest request) {
        String vendorId = authenticatedUserResolver.getCurrentUserId(request);
        if (vendorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Vendor identity could not be resolved. A valid JWT and reachable IAM service are required.");
        }
        return vendorId;
    }

    /**
     * Throws 403 if the given contractId does not belong to the authenticated vendor.
     * Used to guard invoice access — invoice ownership is checked via its contractId.
     */
    private void assertInvoiceOwnership(HttpServletRequest request, String contractId) {
        String vendorId = resolveVendorId(request);
        List<String> vendorContractIds = contractService.getContractIdsByVendorId(vendorId);
        if (!vendorContractIds.contains(contractId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: invoice belongs to a contract that is not associated with your vendor account.");
        }
    }
}
