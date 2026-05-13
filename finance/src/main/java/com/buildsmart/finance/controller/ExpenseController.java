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
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Get expense by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expense found"),
        @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpense(
            @Parameter(description = "Expense ID") @PathVariable String expenseId) {
        log.info("GET /api/expenses/{} - Fetching expense", expenseId);
        return ResponseEntity.ok(expenseService.getExpenseById(expenseId));
    }

    @Operation(summary = "List expenses for a budget", description = "Returns paginated expenses linked to the specified budget")
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
        return ResponseEntity.ok(expenseService.getExpensesByBudgetId(budgetId, pageable));
    }

    @Operation(summary = "List expenses for a project", description = "Returns paginated expenses linked to the specified project")
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
        return ResponseEntity.ok(expenseService.getExpensesByProjectId(projectId, pageable));
    }

    @Operation(summary = "List expenses by status", description = "Status values: APPROVED, PAID")
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
        return ResponseEntity.ok(expenseService.getExpensesByStatus(status, pageable));
    }

    @Operation(summary = "List expenses created by a user")
    @ApiResponse(responseCode = "200", description = "Paginated expense list")
    @GetMapping("/users/{createdBy}")
    public ResponseEntity<PagedResponse<ExpenseResponse>> getExpensesByCreatedBy(
            @Parameter(description = "User ID") @PathVariable String createdBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/expenses/users/{} - Fetching expenses", createdBy);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(expenseService.getExpensesByCreatedBy(createdBy, pageable));
    }

    @Operation(summary = "Update an APPROVED expense", description = "Updates description, amount, and expenseDate. Not allowed once expense is PAID.")
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

    @Operation(summary = "Delete an APPROVED expense", description = "Soft-deletes the expense. Not allowed once expense is PAID.")
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
}
