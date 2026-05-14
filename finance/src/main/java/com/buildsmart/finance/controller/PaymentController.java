package com.buildsmart.finance.controller;

import com.buildsmart.finance.client.dto.InvoiceDto;
import com.buildsmart.finance.dto.request.PaymentApprovalRequest;
import com.buildsmart.finance.dto.request.PaymentCreateRequest;
import com.buildsmart.finance.dto.response.PagedResponse;
import com.buildsmart.finance.dto.response.PaymentResponse;
import com.buildsmart.finance.service.PaymentService;

import java.util.List;
import com.buildsmart.finance.util.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payment REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - FINANCE_OFFICER: sees only payments they created (createdBy = userId from JWT via IAM).
 *   - ADMIN: unrestricted access to all payments.
 *
 * Finance JWT filter resolves userId via IAM Feign call and sets it as the Security principal,
 * so {@code auth.getName()} reliably returns the caller's userId.
 */
@Tag(name = "Payments", description = "Payment processing — create, process, approve/reject and query payment records")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create a new payment",
               description = "Creates a payment record linked to an expense and invoice. Duplicate invoice payments are blocked.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment created successfully"),
        @ApiResponse(responseCode = "400", description = "Duplicate invoice payment or invalid request"),
        @ApiResponse(responseCode = "404", description = "Referenced expense or budget not found")
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentCreateRequest request) {
        log.info("POST /api/payments - Creating payment");
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get payment by ID",
               description = "**FINANCE_OFFICER**: returns 403 if the payment was not created by the calling user.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found"),
        @ApiResponse(responseCode = "403", description = "Finance Officer accessing another user's payment"),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Payment ID") @PathVariable String paymentId) {
        log.info("GET /api/payments/{} - Fetching payment", paymentId);
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        if (isFinanceOfficer()) {
            assertOwnership(response.getCreatedBy(), "payment");
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List payments for an expense",
               description = "**FINANCE_OFFICER**: returns only payments for this expense that were created by the calling user.\n\n" +
                       "**ADMIN**: returns all payments for this expense.")
    @ApiResponse(responseCode = "200", description = "Paginated payment list")
    @GetMapping("/expenses/{expenseId}")
    public ResponseEntity<PagedResponse<PaymentResponse>> getPaymentsByExpense(
            @Parameter(description = "Expense ID") @PathVariable String expenseId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/payments/expenses/{} - Fetching payments", expenseId);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(paymentService.getPaymentsByExpenseIdAndCreatedBy(expenseId, currentUserId, pageable));
        }
        return ResponseEntity.ok(paymentService.getPaymentsByExpenseId(expenseId, pageable));
    }

    @Operation(summary = "List payments by status",
               description = "**FINANCE_OFFICER**: returns only their own payments with the given status.\n\n" +
                       "**ADMIN**: returns all payments with the given status.")
    @ApiResponse(responseCode = "200", description = "Paginated payment list")
    @GetMapping("/status/{status}")
    public ResponseEntity<PagedResponse<PaymentResponse>> getPaymentsByStatus(
            @Parameter(description = "Payment status", example = "PENDING") @PathVariable String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/payments/status/{} - Fetching payments", status);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(paymentService.getPaymentsByStatusAndCreatedBy(status, currentUserId, pageable));
        }
        return ResponseEntity.ok(paymentService.getPaymentsByStatus(status, pageable));
    }

    @Operation(summary = "Update payment status",
               description = "Approves or rejects a pending payment. " +
                       "Approving a payment also recalculates the linked budget's actualAmount idempotently.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @PostMapping("/{paymentId}/status")
    public ResponseEntity<PaymentResponse> updatePaymentStatus(
            @Parameter(description = "Payment ID") @PathVariable String paymentId,
            @Valid @RequestBody PaymentApprovalRequest request) {
        log.info("POST /api/payments/{}/status - Updating payment status", paymentId);
        PaymentResponse response = paymentService.updatePaymentStatus(paymentId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List payments created by a user",
               description = "**FINANCE_OFFICER**: returns 403 if the createdBy param does not match the caller's userId.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponse(responseCode = "200", description = "Paginated payment list")
    @GetMapping("/users/{createdBy}")
    public ResponseEntity<PagedResponse<PaymentResponse>> getPaymentsByCreatedBy(
            @Parameter(description = "User ID") @PathVariable String createdBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/payments/users/{} - Fetching payments", createdBy);
        if (isFinanceOfficer()) {
            assertUserIdMatchesCaller(createdBy, "payments");
        }
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(paymentService.getPaymentsByCreatedBy(createdBy, pageable));
    }

    @Operation(summary = "List pending payments",
               description = "**FINANCE_OFFICER**: returns only pending payments created by the calling user.\n\n" +
                       "**ADMIN**: returns all pending payments.")
    @ApiResponse(responseCode = "200", description = "Paginated pending payment list")
    @GetMapping("/pending")
    public ResponseEntity<PagedResponse<PaymentResponse>> getPendingPayments(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/payments/pending - Fetching pending payments");
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(paymentService.getPendingPaymentsByCreatedBy(currentUserId, pageable));
        }
        return ResponseEntity.ok(paymentService.getPendingPayments(pageable));
    }

    @Operation(
            summary = "List APPROVED invoices from vendor service",
            description = "Fetches all APPROVED vendor invoices via Feign. " +
                    "Use the invoiceId from this list when calling POST /api/payments to settle an invoice. " +
                    "Amount is taken directly from the invoice — do not pass an amount in the payment request.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of APPROVED invoices"),
        @ApiResponse(responseCode = "503", description = "Vendor service unreachable — returns empty list")
    })
    @GetMapping("/invoices/approved")
    public ResponseEntity<List<InvoiceDto>> getApprovedInvoices() {
        log.info("GET /api/payments/invoices/approved - Fetching APPROVED invoices from vendor-service");
        return ResponseEntity.ok(paymentService.getApprovedInvoices());
    }

    @Operation(
            summary = "Get a specific invoice from vendor service",
            description = "Fetches a single vendor invoice by ID via Feign. " +
                    "Use this to verify the invoice amount and status before calling POST /api/payments.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invoice found"),
        @ApiResponse(responseCode = "404", description = "Invoice not found in vendor service")
    })
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<InvoiceDto> getInvoiceFromVendor(
            @Parameter(description = "Invoice ID from vendor service") @PathVariable String invoiceId) {
        log.info("GET /api/payments/invoices/{} - Fetching invoice from vendor-service", invoiceId);
        return ResponseEntity.ok(paymentService.getInvoiceFromVendor(invoiceId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the current user's userId from the Security context (resolved by IAM Feign in the JWT filter). */
    private String resolveCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    /** True when the current JWT holder has the FINANCE_OFFICER role. */
    private boolean isFinanceOfficer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FINANCE_OFFICER"));
    }

    /**
     * Throws 403 if the resource's createdBy does not match the authenticated caller's userId.
     */
    private void assertOwnership(String resourceCreatedBy, String resourceType) {
        String currentUserId = resolveCurrentUserId();
        if (!currentUserId.equals(resourceCreatedBy)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: this " + resourceType + " was not created by your account.");
        }
    }

    /**
     * Throws 403 if the given userId path param does not match the authenticated caller's userId.
     */
    private void assertUserIdMatchesCaller(String userId, String resourceType) {
        String currentUserId = resolveCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you can only view your own " + resourceType + ".");
        }
    }
}
