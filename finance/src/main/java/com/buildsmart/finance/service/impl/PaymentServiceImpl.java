package com.buildsmart.finance.service.impl;

import com.buildsmart.finance.client.NotificationServiceClient;
import com.buildsmart.finance.client.NotificationServiceClient.NotificationPayload;
import com.buildsmart.finance.client.VendorClient;
import com.buildsmart.finance.client.dto.InvoiceDto;
import com.buildsmart.finance.dto.request.PaymentApprovalRequest;
import com.buildsmart.finance.dto.request.PaymentCreateRequest;
import com.buildsmart.finance.dto.response.PagedResponse;
import com.buildsmart.finance.dto.response.PaymentResponse;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.entity.Expense;
import com.buildsmart.finance.entity.Payment;
import com.buildsmart.finance.entity.enums.ExpenseStatus;
import com.buildsmart.finance.entity.enums.PaymentStatus;
import com.buildsmart.finance.exception.BusinessRuleException;
import com.buildsmart.finance.exception.ResourceNotFoundException;
import com.buildsmart.finance.repository.BudgetRepository;
import com.buildsmart.finance.repository.ExpenseRepository;
import com.buildsmart.finance.repository.PaymentRepository;
import com.buildsmart.finance.service.PaymentService;
import com.buildsmart.finance.util.IdGenerator;

import com.buildsmart.finance.validator.PaymentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final PaymentValidator paymentValidator;
    private final VendorClient vendorClient;

    /**
     * Central notification service client — every payment event is routed to
     * the specific vendor (toUserId = vendor's userId on the invoice).
     * Fire-and-forget: failures must never roll back the payment transaction.
     */
    private final NotificationServiceClient notificationServiceClient;

    @Override
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        log.info("Creating payment for invoice: {}", request.getInvoiceId());

        // Fetch invoice from vendor service — amount comes from here, NOT from the request.
        // The invoice also carries the vendorId, which is the recipient for our notification.
        String authHeader = getAuthorizationHeader();
        InvoiceDto invoice = vendorClient.getInvoice(request.getInvoiceId(), authHeader);

        if (invoice == null) {
            throw new ResourceNotFoundException(
                    "FIN-NOT-FOUND-007",
                    request.getInvoiceId(),
                    "Invoice not found with ID: " + request.getInvoiceId()
            );
        }

        if (!"APPROVED".equalsIgnoreCase(invoice.status())) {
            throw new BusinessRuleException(
                    "FIN-BUS-010",
                    "Invoice '" + request.getInvoiceId() + "' is not approved. " +
                            "Current status: " + invoice.status() + ". Only APPROVED invoices can be paid."
            );
        }

        Expense expense = expenseRepository.findById(request.getExpenseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-003",
                        request.getExpenseId(),
                        "Expense not found with ID: " + request.getExpenseId()
                ));

        paymentValidator.validatePaymentCreation(request, expense, invoice.amount());
        paymentValidator.checkDuplicatePayment(request.getInvoiceId());

        String paymentId = IdGenerator.generatePaymentId();
        // Read userId from SecurityContext — filter already resolved it from IAM.
        // JWT parsing is unreliable here due to key-derivation mismatch between IAM and finance.
        String createdBy = resolveCurrentUserId();

        // vendorId is not returned by vendor-service (field is null in InvoiceDto).
        // Use submittedBy (vendor's display name) as the best available identifier.
        String vendorIdentifier = invoice.vendorId() != null && !invoice.vendorId().isBlank()
                ? invoice.vendorId()
                : invoice.submittedBy();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .invoiceId(request.getInvoiceId())
                .expenseId(request.getExpenseId())
                .budgetId(expense.getBudgetId())
                .vendorId(vendorIdentifier)
                .amount(invoice.amount())
                .paymentMethod(request.getPaymentMethod())
                .bankReferenceNumber(request.getBankReferenceNumber())
                .status(PaymentStatus.INITIATED)
                .createdBy(createdBy)
                .isDeleted(false)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created successfully with ID: {}", paymentId);

        publishPaymentInitiatedEvent(savedPayment);

        // --- Push PAYMENT_CREATED to the vendor ---
        pushCentral(
                "PAYMENT_CREATED",
                String.format("Payment %s of amount %s initiated for invoice %s.",
                        savedPayment.getPaymentId(), savedPayment.getAmount(),
                        savedPayment.getInvoiceId()),
                createdBy,
                "VENDOR",
                vendorIdentifier,
                savedPayment.getPaymentId());

        return mapToResponse(savedPayment);
    }

    @Override
    public PaymentResponse getPaymentById(String paymentId) {
        log.info("Fetching payment with ID: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-004",
                        paymentId,
                        "Payment not found with ID: " + paymentId
                ));
        return mapToResponse(payment);
    }

    @Override
    public PagedResponse<PaymentResponse> getPaymentsByExpenseId(String expenseId, Pageable pageable) {
        log.info("Fetching payments for expense: {}", expenseId);
        Page<Payment> payments = paymentRepository.findByExpenseId(expenseId, pageable);
        return buildPagedResponse(payments);
    }

    @Override
    public PagedResponse<PaymentResponse> getPaymentsByStatus(String status, Pageable pageable) {
        log.info("Fetching payments with status: {}", status);
        PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
        Page<Payment> payments = paymentRepository.findByStatus(paymentStatus, pageable);
        return buildPagedResponse(payments);
    }

    @Override
    public PaymentResponse updatePaymentStatus(String paymentId, PaymentApprovalRequest request) {
        log.info("Updating payment status for ID: {} to: {}", paymentId, request.getStatus());

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-004",
                        paymentId,
                        "Payment not found with ID: " + paymentId
                ));

        if (request.getStatus() == PaymentStatus.REJECTED) {
            paymentValidator.validatePaymentCanBeRejected(payment.getStatus().name());
        }

        payment.setStatus(request.getStatus());
        payment.setApprovedBy(request.getApprovedBy());
        payment.setApprovedAt(LocalDateTime.now());

        if (request.getStatus() == PaymentStatus.REJECTED) {
            payment.setRejectionReason(request.getRejectionReason());
        }

        if (request.getStatus() == PaymentStatus.COMPLETED) {
            payment.setPaymentDate(LocalDateTime.now());
            updateBudgetActualAmount(payment);
            markExpenseAsPaid(payment.getExpenseId());
        }

        Payment updatedPayment = paymentRepository.save(payment);
        log.info("Payment {} status updated to: {}", paymentId, request.getStatus());

        publishPaymentStatusChangedEvent(updatedPayment);

        // --- Push PAYMENT_RELEASED or rejection to the vendor ---
        // We need the vendorId — look it up via the invoice on the payment.
        String vendorIdForNotification = resolveVendorIdForPayment(updatedPayment);

        if (request.getStatus() == PaymentStatus.COMPLETED) {
            String releasedMsg = String.format(
                    "Payment %s for invoice %s of amount %s has been RELEASED.",
                    updatedPayment.getPaymentId(),
                    updatedPayment.getInvoiceId(),
                    updatedPayment.getAmount());
            pushCentral("PAYMENT_RELEASED", releasedMsg,
                    updatedPayment.getApprovedBy(), "VENDOR", vendorIdForNotification,
                    updatedPayment.getPaymentId());
        } else if (request.getStatus() == PaymentStatus.REJECTED) {
            String rejectMsg = String.format(
                    "Payment %s for invoice %s was REJECTED. Reason: %s",
                    updatedPayment.getPaymentId(),
                    updatedPayment.getInvoiceId(),
                    updatedPayment.getRejectionReason() != null
                            ? updatedPayment.getRejectionReason() : "(no reason provided)");
            pushCentral("APPROVAL_REJECTED", rejectMsg,
                    updatedPayment.getApprovedBy(), "VENDOR", vendorIdForNotification,
                    updatedPayment.getPaymentId());
        }

        return mapToResponse(updatedPayment);
    }

    @Override
    public PagedResponse<PaymentResponse> getPaymentsByCreatedBy(String createdBy, Pageable pageable) {
        log.info("Fetching payments created by: {}", createdBy);
        Page<Payment> payments = paymentRepository.findByCreatedBy(createdBy, pageable);
        return buildPagedResponse(payments);
    }

    @Override
    public PagedResponse<PaymentResponse> getPaymentsByExpenseIdAndCreatedBy(String expenseId, String createdBy, Pageable pageable) {
        log.info("Fetching payments for expense: {} created by: {}", expenseId, createdBy);
        Page<Payment> payments = paymentRepository.findByExpenseIdAndCreatedBy(expenseId, createdBy, pageable);
        return buildPagedResponse(payments);
    }

    @Override
    public PagedResponse<PaymentResponse> getPaymentsByStatusAndCreatedBy(String status, String createdBy, Pageable pageable) {
        log.info("Fetching payments with status: {} created by: {}", status, createdBy);
        PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
        Page<Payment> payments = paymentRepository.findByStatusAndCreatedBy(paymentStatus, createdBy, pageable);
        return buildPagedResponse(payments);
    }

    @Override
    public PagedResponse<PaymentResponse> getPendingPaymentsByCreatedBy(String createdBy, Pageable pageable) {
        log.info("Fetching pending payments created by: {}", createdBy);
        List<Payment> pendingPayments = paymentRepository.findPendingPaymentsByCreatedBy(createdBy);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), pendingPayments.size());
        List<Payment> pagedPayments = new ArrayList<>();
        if (start < pendingPayments.size()) {
            pagedPayments = pendingPayments.subList(start, end);
        }
        Page<Payment> page = new PageImpl<>(pagedPayments, pageable, pendingPayments.size());
        return buildPagedResponse(page);
    }

    @Override
    public PagedResponse<PaymentResponse> getPendingPayments(Pageable pageable) {
        log.info("Fetching pending payments");
        List<Payment> pendingPayments = paymentRepository.findPendingPayments();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), pendingPayments.size());
        List<Payment> pagedPayments = new ArrayList<>();
        if (start < pendingPayments.size()) {
            pagedPayments = pendingPayments.subList(start, end);
        }
        Page<Payment> page = new PageImpl<>(pagedPayments, pageable, pendingPayments.size());
        return buildPagedResponse(page);
    }

    private void updateBudgetActualAmount(Payment payment) {
        log.info("Updating budget actual amount for payment: {}", payment.getPaymentId());

        Expense expense = expenseRepository.findById(payment.getExpenseId()).orElse(null);
        if (expense == null) {
            log.warn("Expense not found for payment: {}", payment.getPaymentId());
            return;
        }

        Budget budget = budgetRepository.findById(expense.getBudgetId()).orElse(null);
        if (budget == null) {
            log.warn("Budget not found for expense: {}", expense.getExpenseId());
            return;
        }

        // Recalculate from all previously COMPLETED payments for this budget,
        // then add the current payment — idempotent, safe against retries.
        java.math.BigDecimal alreadyPaid = paymentRepository
                .findCompletedPaymentsByBudgetId(budget.getBudgetId())
                .stream()
                .map(Payment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        budget.setActualAmount(alreadyPaid.add(payment.getAmount()));
        budget.calculateVariance();
        budgetRepository.save(budget);
        log.info("Budget {} actualAmount recalculated to {} after payment {}",
                budget.getBudgetId(), budget.getActualAmount(), payment.getPaymentId());
    }

    private void markExpenseAsPaid(String expenseId) {
        expenseRepository.findById(expenseId).ifPresentOrElse(expense -> {
            expense.setStatus(ExpenseStatus.PAID);
            expenseRepository.save(expense);
            log.info("Expense {} marked as PAID", expenseId);
        }, () -> log.warn("Expense {} not found when marking PAID — skipping", expenseId));
    }

    /**
     * Returns the stored vendorId from the payment record — captured at creation
     * time so no Feign call is needed on status updates.
     */
    private String resolveVendorIdForPayment(Payment payment) {
        return payment.getVendorId();
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .invoiceId(payment.getInvoiceId())
                .expenseId(payment.getExpenseId())
                .budgetId(payment.getBudgetId())
                .vendorId(payment.getVendorId())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .bankReferenceNumber(payment.getBankReferenceNumber())
                .createdBy(payment.getCreatedBy())
                .approvedBy(payment.getApprovedBy())
                .approvedAt(payment.getApprovedAt())
                .rejectionReason(payment.getRejectionReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private PagedResponse<PaymentResponse> buildPagedResponse(Page<Payment> page) {
        return PagedResponse.<PaymentResponse>builder()
                .content(page.getContent().stream().map(this::mapToResponse).toList())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void publishPaymentInitiatedEvent(Payment payment) {
        log.info("Publishing PaymentInitiatedEvent for payment: {}", payment.getPaymentId());
    }

    private void publishPaymentStatusChangedEvent(Payment payment) {
        log.info("Publishing PaymentStatusChangedEvent for payment: {}", payment.getPaymentId());
    }

    private String getAuthorizationHeader() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object credentials = authentication.getCredentials();
                if (credentials != null) {
                    String token = credentials.toString();
                    String bearerToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
                    log.debug("Extracted authorization header from security context");
                    return bearerToken;
                }
            }
            log.warn("Could not extract authorization header from security context");
            return null;
        } catch (Exception e) {
            log.error("Error extracting authorization header: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Reads userId from the Spring Security principal.
     * JwtAuthenticationFilter already resolved userId (e.g. "BSFO001") from IAM
     * and stored it as the principal — no JWT parsing needed here.
     */
    private String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equals(auth.getName())) {
            log.debug("Resolved userId from SecurityContext: {}", auth.getName());
            return auth.getName();
        }
        log.warn("SecurityContext has no authenticated principal — defaulting to UNKNOWN");
        return "UNKNOWN";
    }

    @Override
    public List<InvoiceDto> getApprovedInvoices() {
        log.info("Fetching APPROVED invoices from vendor-service");
        String authHeader = getAuthorizationHeader();
        List<InvoiceDto> invoices = vendorClient.getInvoicesByStatus("APPROVED", authHeader);
        log.info("Found {} APPROVED invoices from vendor-service", invoices.size());
        return invoices;
    }

    @Override
    public InvoiceDto getInvoiceFromVendor(String invoiceId) {
        log.info("Fetching invoice {} from vendor-service", invoiceId);
        String authHeader = getAuthorizationHeader();
        InvoiceDto invoice = vendorClient.getInvoice(invoiceId, authHeader);
        if (invoice == null) {
            throw new com.buildsmart.finance.exception.ResourceNotFoundException(
                    "FIN-NOT-FOUND-007",
                    invoiceId,
                    "Invoice not found in vendor-service: " + invoiceId
            );
        }
        return invoice;
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
            notificationServiceClient.create(new NotificationPayload(
                    eventType,
                    message,
                    "finance-service",
                    "FINANCE_OFFICER",
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