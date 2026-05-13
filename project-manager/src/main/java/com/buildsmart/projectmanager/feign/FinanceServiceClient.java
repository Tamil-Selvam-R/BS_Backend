package com.buildsmart.projectmanager.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PM-side Feign client for the Finance microservice.
 *
 * The PM is the designated approver for all Finance budgets and expenses.
 * Finance Officers create and submit budgets/expenses; PM approves or rejects them.
 *
 * Eureka service name: finance-service (port 8085).
 */
@FeignClient(
        name = "finance-service",
        configuration = FeignClientConfig.class,
        contextId = "pmFinanceClient"
)
public interface FinanceServiceClient {

    // ── Budgets ───────────────────────────────────────────────────────────────

    @GetMapping("/api/budgets/status/{status}")
    List<BudgetDto> getBudgetsByStatus(@PathVariable("status") String status);

    @GetMapping("/api/budgets/projects/{projectId}")
    Object getBudgetsByProject(@PathVariable("projectId") String projectId);

    @PostMapping("/api/budgets/{budgetId}/approval")
    BudgetDto approveBudget(
            @PathVariable("budgetId") String budgetId,
            @RequestBody BudgetApprovalRequest request);

    // ── Expenses ──────────────────────────────────────────────────────────────

    @GetMapping("/api/expenses/status/{status}")
    List<ExpenseDto> getExpensesByStatus(@PathVariable("status") String status);

    @PostMapping("/api/expenses/{expenseId}/approval")
    ExpenseDto approveExpense(
            @PathVariable("expenseId") String expenseId,
            @RequestBody ExpenseApprovalRequest request);

    // ── DTOs (mirrors Finance service request/response shapes) ────────────────

    record BudgetApprovalRequest(
            String status,           // "APPROVED" or "REJECTED"
            String approvedBy,       // PM user ID
            String rejectionReason   // mandatory when REJECTED
    ) {}

    record ExpenseApprovalRequest(
            String status,           // "APPROVED" or "REJECTED"
            String approvedBy,       // PM user ID
            String rejectionReason,  // mandatory when REJECTED
            String revisionReason    // optional
    ) {}

    record BudgetDto(
            String budgetId,
            String projectId,
            String budgetCategory,
            Double plannedAmount,
            Double actualAmount,
            Double variance,
            String status,
            String rejectionReason,
            String createdBy
    ) {}

    record ExpenseDto(
            String expenseId,
            String budgetId,
            String projectId,
            String expenseCategory,
            Double amount,
            String status,
            String rejectionReason,
            String createdBy
    ) {}
}
