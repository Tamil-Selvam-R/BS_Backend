package com.buildsmart.finance.service;

import com.buildsmart.finance.dto.request.ExpenseCreateRequest;
import com.buildsmart.finance.dto.request.ExpenseUpdateRequest;
import com.buildsmart.finance.dto.response.ExpenseResponse;
import com.buildsmart.finance.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {

    /**
     * Create a new expense under an APPROVED budget.
     * LABOUR: amount from request (manual entry).
     * Other types: amount fetched from the APPROVED vendor invoice via invoiceId.
     * Expense is saved directly as APPROVED — no PM approval step needed.
     */
    ExpenseResponse createExpense(ExpenseCreateRequest request);

    /**
     * Get expense by ID
     */
    ExpenseResponse getExpenseById(String expenseId);

    /**
     * Get all expenses for a budget with pagination
     */
    PagedResponse<ExpenseResponse> getExpensesByBudgetId(String budgetId, Pageable pageable);

    /**
     * Get all expenses for a project with pagination
     */
    PagedResponse<ExpenseResponse> getExpensesByProjectId(String projectId, Pageable pageable);

    /**
     * Get expenses by status with pagination
     */
    PagedResponse<ExpenseResponse> getExpensesByStatus(String status, Pageable pageable);

    /**
     * Get expenses created by user
     */
    PagedResponse<ExpenseResponse> getExpensesByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Update expense — only allowed while status is APPROVED (not PAID)
     */
    ExpenseResponse updateExpense(String expenseId, ExpenseUpdateRequest request);

    /**
     * Soft-delete expense — only allowed while status is APPROVED (not PAID)
     */
    void deleteExpense(String expenseId);
}
