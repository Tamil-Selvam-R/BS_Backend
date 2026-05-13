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
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments", description = "Payment processing — create, process, approve/reject and query payment records")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create a new payment", description = "Creates a payment record linked to an expense and invoice. Duplicate invoice payments are blocked.")
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

    @Operation(summary = "Get payment by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found"),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Payment ID") @PathVariable String paymentId) {
        log.info("GET /api/payments/{} - Fetching payment", paymentId);
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List payments for an expense", description = "Returns paginated payments linked to the specified expense")
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
        PagedResponse<PaymentResponse> response = paymentService.getPaymentsByExpenseId(expenseId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List payments by status", description = "Returns paginated payments filtered by status (PENDING, COMPLETED, REJECTED)")
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
        PagedResponse<PaymentResponse> response = paymentService.getPaymentsByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update payment status", description = "Approves or rejects a pending payment. Approving a payment also recalculates the linked budget's actualAmount idempotently.")
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

    @Operation(summary = "List payments created by a user")
    @ApiResponse(responseCode = "200", description = "Paginated payment list")
    @GetMapping("/users/{createdBy}")
    public ResponseEntity<PagedResponse<PaymentResponse>> getPaymentsByCreatedBy(
            @Parameter(description = "User ID (email)") @PathVariable String createdBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/payments/users/{} - Fetching payments", createdBy);

        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        PagedResponse<PaymentResponse> response = paymentService.getPaymentsByCreatedBy(createdBy, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List pending payments", description = "Returns all payments currently in PENDING status awaiting approval")
    @ApiResponse(responseCode = "200", description = "Paginated pending payment list")
    @GetMapping("/pending")
    public ResponseEntity<PagedResponse<PaymentResponse>> getPendingPayments(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/payments/pending - Fetching pending payments");

        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        PagedResponse<PaymentResponse> response = paymentService.getPendingPayments(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/payments/invoices/approved
     * Lists all APPROVED invoices from the vendor service.
     * Finance Officer uses this to discover which vendor invoices are ready to be paid.
     * The invoiceId from this list is what you pass to POST /api/payments to create a payment.
     */
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

    /**
     * GET /api/payments/invoices/{invoiceId}
     * Fetches a single invoice from the vendor service.
     * Finance Officer calls this to preview the invoice details (amount, description,
     * contractId, submittedBy) before creating a payment against it.
     */
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
}
