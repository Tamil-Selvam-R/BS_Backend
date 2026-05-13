package com.buildsmart.finance.service.impl;

import com.buildsmart.finance.client.VendorClient;
import com.buildsmart.finance.client.dto.InvoiceDto;
import com.buildsmart.finance.dto.request.ExpenseCreateRequest;
import com.buildsmart.finance.dto.request.ExpenseUpdateRequest;
import com.buildsmart.finance.dto.response.ExpenseResponse;
import com.buildsmart.finance.dto.response.PagedResponse;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.entity.Expense;
import com.buildsmart.finance.entity.enums.ExpenseStatus;
import com.buildsmart.finance.exception.BusinessRuleException;
import com.buildsmart.finance.exception.ResourceNotFoundException;
import com.buildsmart.finance.repository.BudgetRepository;
import com.buildsmart.finance.repository.ExpenseRepository;
import com.buildsmart.finance.service.ExpenseService;
import com.buildsmart.finance.util.IdGenerator;
import com.buildsmart.finance.validator.ExpenseValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LABOUR_TYPE = "LABOUR";

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseValidator expenseValidator;
    private final VendorClient vendorClient;

    @Override
    public ExpenseResponse createExpense(ExpenseCreateRequest request) {
        log.info("Creating expense for budget: {}", request.getBudgetId());

        Budget budget = budgetRepository.findById(request.getBudgetId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-002",
                        request.getBudgetId(),
                        "Budget not found with ID: " + request.getBudgetId()
                ));

        BigDecimal expenseAmount;
        String resolvedInvoiceId = request.getInvoiceId();

        boolean isLabour = LABOUR_TYPE.equalsIgnoreCase(request.getExpenseType());

        if (!isLabour && resolvedInvoiceId != null && !resolvedInvoiceId.isBlank()) {
            // Non-LABOUR: fetch amount from vendor invoice
            String authHeader = getAuthorizationHeader();
            InvoiceDto invoice = vendorClient.getInvoice(resolvedInvoiceId, authHeader);
            if (invoice == null) {
                throw new ResourceNotFoundException(
                        "FIN-NOT-FOUND-007",
                        resolvedInvoiceId,
                        "Invoice not found in vendor-service: " + resolvedInvoiceId
                );
            }
            if (!"APPROVED".equalsIgnoreCase(invoice.status())) {
                throw new BusinessRuleException(
                        "FIN-BUS-010",
                        "Invoice '" + resolvedInvoiceId + "' is not APPROVED. " +
                                "Current status: " + invoice.status() + ". Only APPROVED invoices can be expensed."
                );
            }
            expenseAmount = invoice.amount();
            log.info("Amount {} resolved from invoice {} for expense type {}", expenseAmount, resolvedInvoiceId, request.getExpenseType());
        } else {
            // LABOUR or no invoiceId: amount must be provided manually
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(
                        "FIN-BUS-012",
                        isLabour
                                ? "Amount is required for LABOUR expenses (manual entry — enter monthly labour cost)"
                                : "Amount is required when invoiceId is not provided"
                );
            }
            expenseAmount = request.getAmount();
        }

        // Remaining budget check
        BigDecimal alreadySpent = expenseRepository.sumActiveExpensesByBudgetId(budget.getBudgetId());
        expenseValidator.validateExpenseCreation(budget, expenseAmount, alreadySpent);

        String expenseId = IdGenerator.generateExpenseId();
        String createdBy = resolveCurrentUserId();

        Expense expense = Expense.builder()
                .expenseId(expenseId)
                .projectId(request.getProjectId())
                .budgetId(request.getBudgetId())
                .expenseType(request.getExpenseType())
                .invoiceId(resolvedInvoiceId)
                .description(request.getDescription())
                .amount(expenseAmount)
                .expenseDate(request.getExpenseDate())
                .status(ExpenseStatus.APPROVED)
                .createdBy(createdBy)
                .approvedBy(createdBy)
                .approvedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        Expense savedExpense = expenseRepository.save(expense);
        log.info("Expense {} created and auto-approved under budget {}", expenseId, request.getBudgetId());

        BigDecimal remainingAfter = budget.getPlannedAmount().subtract(alreadySpent.add(expenseAmount));
        return mapToResponse(savedExpense, remainingAfter);
    }

    @Override
    public ExpenseResponse getExpenseById(String expenseId) {
        log.info("Fetching expense with ID: {}", expenseId);
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-003",
                        expenseId,
                        "Expense not found with ID: " + expenseId
                ));
        return mapToResponse(expense, null);
    }

    @Override
    public PagedResponse<ExpenseResponse> getExpensesByBudgetId(String budgetId, Pageable pageable) {
        log.info("Fetching expenses for budget: {}", budgetId);
        Page<Expense> expenses = expenseRepository.findByBudgetId(budgetId, pageable);
        return buildPagedResponse(expenses);
    }

    @Override
    public PagedResponse<ExpenseResponse> getExpensesByProjectId(String projectId, Pageable pageable) {
        log.info("Fetching expenses for project: {}", projectId);
        Page<Expense> expenses = expenseRepository.findByProjectId(projectId, pageable);
        return buildPagedResponse(expenses);
    }

    @Override
    public PagedResponse<ExpenseResponse> getExpensesByStatus(String status, Pageable pageable) {
        log.info("Fetching expenses with status: {}", status);
        ExpenseStatus expenseStatus = ExpenseStatus.valueOf(status.toUpperCase());
        Page<Expense> expenses = expenseRepository.findByStatus(expenseStatus, pageable);
        return buildPagedResponse(expenses);
    }

    @Override
    public PagedResponse<ExpenseResponse> getExpensesByCreatedBy(String createdBy, Pageable pageable) {
        log.info("Fetching expenses created by: {}", createdBy);
        Page<Expense> expenses = expenseRepository.findByCreatedBy(createdBy, pageable);
        return buildPagedResponse(expenses);
    }

    @Override
    public ExpenseResponse updateExpense(String expenseId, ExpenseUpdateRequest request) {
        log.info("Updating expense with ID: {}", expenseId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-003",
                        expenseId,
                        "Expense not found with ID: " + expenseId
                ));

        expenseValidator.validateExpenseUpdate(request);
        expenseValidator.validateExpenseCanBeEdited(expense);

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate().atStartOfDay());

        Expense updatedExpense = expenseRepository.save(expense);
        log.info("Expense {} updated successfully", expenseId);
        return mapToResponse(updatedExpense, null);
    }

    @Override
    public void deleteExpense(String expenseId) {
        log.info("Deleting expense with ID: {}", expenseId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-003",
                        expenseId,
                        "Expense not found with ID: " + expenseId
                ));

        expenseValidator.validateExpenseCanBeDeleted(expense);
        expense.setIsDeleted(true);
        expenseRepository.save(expense);
        log.info("Expense {} soft-deleted", expenseId);
    }

    private ExpenseResponse mapToResponse(Expense expense, BigDecimal remainingBudget) {
        return ExpenseResponse.builder()
                .expenseId(expense.getExpenseId())
                .projectId(expense.getProjectId())
                .budgetId(expense.getBudgetId())
                .expenseType(expense.getExpenseType())
                .invoiceId(expense.getInvoiceId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate())
                .status(expense.getStatus())
                .createdBy(expense.getCreatedBy())
                .approvedBy(expense.getApprovedBy())
                .approvedAt(expense.getApprovedAt())
                .rejectionReason(expense.getRejectionReason())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .remainingBudget(remainingBudget)
                .build();
    }

    private PagedResponse<ExpenseResponse> buildPagedResponse(Page<Expense> page) {
        return PagedResponse.<ExpenseResponse>builder()
                .content(page.getContent().stream().map(e -> mapToResponse(e, null)).toList())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .timestamp(LocalDateTime.now())
                .build();
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

    private String getAuthorizationHeader() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) return null;
            Object credentials = authentication.getCredentials();
            if (credentials == null) return null;
            String token = credentials.toString();
            return token.startsWith(BEARER_PREFIX) ? token : BEARER_PREFIX + token;
        } catch (Exception e) {
            log.error("Error extracting authorization header: {}", e.getMessage());
            return null;
        }
    }
}
