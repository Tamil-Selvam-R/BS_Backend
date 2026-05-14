package com.buildsmart.finance.controller;

import com.buildsmart.finance.dto.request.ExpenseCreateRequest;
import com.buildsmart.finance.dto.request.ExpenseUpdateRequest;
import com.buildsmart.finance.dto.response.ExpenseResponse;
import com.buildsmart.finance.dto.response.PagedResponse;
import com.buildsmart.finance.service.ExpenseService;
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
 * Expense REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - FINANCE_OFFICER: sees only expenses they created (createdBy = userId from JWT via IAM).
 *   - ADMIN: unrestricted access to all expenses.
 *
 * Finance JWT filter resolves userId via IAM Feign call and sets it as the Security principal,
 * so {@code auth.getName()} reliably returns the caller's userId.
 */
@Tag(name = "Expenses", description = "Expense tracking — create, query, update and delete expense records")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(
            summary = "Create a new expense",
            description = "Creates an expense directly as APPROVED under an APPROVED budget. " +
                    "LABOUR: provide amount manually. " +
                    "Other types (MATERIAL, EQUIPMENT, OVERHEAD, CONTINGENCY): provide invoiceId — amount is fetched from the APPROVED vendor invoice.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Expense created and approved"),
        @ApiResponse(responseCode = "400", description = "Amount exceeds remaining budget or invoice not APPROVED"),
        @ApiResponse(responseCode = "404", description = "Budget or invoice not found")
    })
    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @Valid @RequestBody ExpenseCreateRequest request) {
        log.info("POST /api/expenses - Creating expense");
        ExpenseResponse response = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get expense by ID",
               description = "**FINANCE_OFFICER**: returns 403 if the expense was not created by the calling user.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expense found"),
        @ApiResponse(responseCode = "403", description = "Finance Officer accessing another user's expense"),
        @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpense(
            @Parameter(description = "Expense ID") @PathVariable String expenseId) {
        log.info("GET /api/expenses/{} - Fetching expense", expenseId);
        ExpenseResponse response = expenseService.getExpenseById(expenseId);
        if (isFinanceOfficer()) {
            assertOwnership(response.getCreatedBy(), "expense");
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List expenses for a budget",
               description = "**FINANCE_OFFICER**: returns only expenses for this budget that were created by the calling user.\n\n" +
                       "**ADMIN**: returns all expenses for this budget.")
    @ApiResponse(responseCode = "200", description = "Paginated expense list")
    @GetMapping("/budgets/{budgetId}")
    public ResponseEntity<PagedResponse<ExpenseResponse>> getExpensesByBudget(
            @Parameter(description = "Budget ID") @PathVariable String budgetId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/expenses/budgets/{} - Fetching expenses", budgetId);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(expenseService.getExpensesByBudgetIdAndCreatedBy(budgetId, currentUserId, pageable));
        }
        return ResponseEntity.ok(expenseService.getExpensesByBudgetId(budgetId, pageable));
    }

    @Operation(summary = "List expenses for a project",
               description = "**FINANCE_OFFICER**: returns only expenses for this project that were created by the calling user.\n\n" +
                       "**ADMIN**: returns all expenses for this project.")
    @ApiResponse(responseCode = "200", description = "Paginated expense list")
    @GetMapping("/projects/{projectId}")
    public ResponseEntity<PagedResponse<ExpenseResponse>> getExpensesByProject(
            @Parameter(description = "Project ID") @PathVariable String projectId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/expenses/projects/{} - Fetching expenses", projectId);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(expenseService.getExpensesByProjectIdAndCreatedBy(projectId, currentUserId, pageable));
        }
        return ResponseEntity.ok(expenseService.getExpensesByProjectId(projectId, pageable));
    }

    @Operation(summary = "List expenses by status",
               description = "**FINANCE_OFFICER**: returns only their own expenses with the given status.\n\n" +
                       "**ADMIN**: returns all expenses with the given status.")
    @ApiResponse(responseCode = "200", description = "Paginated expense list")
    @GetMapping("/status/{status}")
    public ResponseEntity<PagedResponse<ExpenseResponse>> getExpensesByStatus(
            @Parameter(description = "Expense status", example = "APPROVED") @PathVariable String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/expenses/status/{} - Fetching expenses", status);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(expenseService.getExpensesByStatusAndCreatedBy(status, currentUserId, pageable));
        }
        return ResponseEntity.ok(expenseService.getExpensesByStatus(status, pageable));
    }

    @Operation(summary = "List expenses created by a user",
               description = "**FINANCE_OFFICER**: returns 403 if the createdBy param does not match the caller's userId.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponse(responseCode = "200", description = "Paginated expense list")
    @GetMapping("/users/{createdBy}")
    public ResponseEntity<PagedResponse<ExpenseResponse>> getExpensesByCreatedBy(
            @Parameter(description = "User ID") @PathVariable String createdBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/expenses/users/{} - Fetching expenses", createdBy);
        if (isFinanceOfficer()) {
            assertUserIdMatchesCaller(createdBy, "expenses");
        }
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(expenseService.getExpensesByCreatedBy(createdBy, pageable));
    }

    @Operation(summary = "Update an APPROVED expense",
               description = "Updates description, amount, and expenseDate. Not allowed once expense is PAID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expense updated"),
        @ApiResponse(responseCode = "400", description = "Expense is not in APPROVED status"),
        @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    @PatchMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @Parameter(description = "Expense ID") @PathVariable String expenseId,
            @Valid @RequestBody ExpenseUpdateRequest request) {
        log.info("PATCH /api/expenses/{} - Updating expense", expenseId);
        return ResponseEntity.ok(expenseService.updateExpense(expenseId, request));
    }

    @Operation(summary = "Delete an APPROVED expense",
               description = "Soft-deletes the expense. Not allowed once expense is PAID.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Expense deleted"),
        @ApiResponse(responseCode = "400", description = "Expense is PAID and cannot be deleted"),
        @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @Parameter(description = "Expense ID") @PathVariable String expenseId) {
        log.info("DELETE /api/expenses/{} - Deleting expense", expenseId);
        expenseService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
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
