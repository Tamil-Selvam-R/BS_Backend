package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.feign.FinanceServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PM-side endpoints for approving / rejecting Finance budgets and expenses.
 *
 * Lifecycle (Feature Set 3 + 4 Common Approval Engine):
 *  Finance Officer creates Budget/Expense → submits (status = SUBMITTED)
 *  → PM reviews via GET endpoints below
 *  → PM approves or rejects via POST endpoints below
 *  → Finance service updates status and records rejection reason if applicable
 *  → Rejected items can be revised and resubmitted (handled in Finance service)
 */
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Finance Approval (PM)", description = "PM approval of Finance budgets and expenses")
public class FinanceApprovalController {

    private final FinanceServiceClient financeServiceClient;

    // ── Budgets ───────────────────────────────────────────────────────────────

    /**
     * GET /api/finance/budgets/pending
     * Returns all budgets in SUBMITTED status awaiting PM review.
     */
    @GetMapping("/budgets/pending")
    @Operation(summary = "Get budgets awaiting PM approval",
               description = "Fetches Finance budgets with status SUBMITTED for PM review.")
    public ResponseEntity<List<FinanceServiceClient.BudgetDto>> getPendingBudgets() {
        return ResponseEntity.ok(financeServiceClient.getBudgetsByStatus("SUBMITTED"));
    }

    /**
     * POST /api/finance/budgets/{budgetId}/approve
     * PM approves a Finance budget.
     */
    @PostMapping("/budgets/{budgetId}/approve")
    @Operation(summary = "Approve a Finance budget")
    public ResponseEntity<FinanceServiceClient.BudgetDto> approveBudget(@PathVariable String budgetId) {
        String pmId = resolvePmUserId();
        FinanceServiceClient.BudgetApprovalRequest req =
                new FinanceServiceClient.BudgetApprovalRequest("APPROVED", pmId, null);
        FinanceServiceClient.BudgetDto result = financeServiceClient.approveBudget(budgetId, req);
        log.info("PM '{}' approved Finance budget '{}'", pmId, budgetId);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/finance/budgets/{budgetId}/reject
     * PM rejects a Finance budget. rejectionReason is MANDATORY.
     *
     * Request body: { "rejectionReason": "Budget exceeds project allocation" }
     */
    @PostMapping("/budgets/{budgetId}/reject")
    @Operation(summary = "Reject a Finance budget",
               description = "PM rejects the budget. rejectionReason is mandatory.")
    public ResponseEntity<FinanceServiceClient.BudgetDto> rejectBudget(
            @PathVariable String budgetId,
            @RequestBody Map<String, String> body) {

        String rejectionReason = body.get("rejectionReason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String pmId = resolvePmUserId();
        FinanceServiceClient.BudgetApprovalRequest req =
                new FinanceServiceClient.BudgetApprovalRequest("REJECTED", pmId, rejectionReason);
        FinanceServiceClient.BudgetDto result = financeServiceClient.approveBudget(budgetId, req);
        log.info("PM '{}' rejected Finance budget '{}'. Reason: {}", pmId, budgetId, rejectionReason);
        return ResponseEntity.ok(result);
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    /**
     * GET /api/finance/expenses/pending
     * Returns all expenses in SUBMITTED status awaiting PM review.
     */
    @GetMapping("/expenses/pending")
    @Operation(summary = "Get expenses awaiting PM approval",
               description = "Fetches Finance expenses with status SUBMITTED for PM review.")
    public ResponseEntity<List<FinanceServiceClient.ExpenseDto>> getPendingExpenses() {
        return ResponseEntity.ok(financeServiceClient.getExpensesByStatus("SUBMITTED"));
    }

    /**
     * POST /api/finance/expenses/{expenseId}/approve
     * PM approves a Finance expense.
     */
    @PostMapping("/expenses/{expenseId}/approve")
    @Operation(summary = "Approve a Finance expense")
    public ResponseEntity<FinanceServiceClient.ExpenseDto> approveExpense(@PathVariable String expenseId) {
        String pmId = resolvePmUserId();
        FinanceServiceClient.ExpenseApprovalRequest req =
                new FinanceServiceClient.ExpenseApprovalRequest("APPROVED", pmId, null, null);
        FinanceServiceClient.ExpenseDto result = financeServiceClient.approveExpense(expenseId, req);
        log.info("PM '{}' approved Finance expense '{}'", pmId, expenseId);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/finance/expenses/{expenseId}/reject
     * PM rejects a Finance expense. rejectionReason is MANDATORY.
     *
     * Request body: { "rejectionReason": "Expense not within approved budget category" }
     */
    @PostMapping("/expenses/{expenseId}/reject")
    @Operation(summary = "Reject a Finance expense",
               description = "PM rejects the expense. rejectionReason is mandatory.")
    public ResponseEntity<FinanceServiceClient.ExpenseDto> rejectExpense(
            @PathVariable String expenseId,
            @RequestBody Map<String, String> body) {

        String rejectionReason = body.get("rejectionReason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String pmId = resolvePmUserId();
        FinanceServiceClient.ExpenseApprovalRequest req =
                new FinanceServiceClient.ExpenseApprovalRequest("REJECTED", pmId, rejectionReason, null);
        FinanceServiceClient.ExpenseDto result = financeServiceClient.approveExpense(expenseId, req);
        log.info("PM '{}' rejected Finance expense '{}'. Reason: {}", pmId, expenseId, rejectionReason);
        return ResponseEntity.ok(result);
    }

    private String resolvePmUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "unknown-pm";
    }
}
