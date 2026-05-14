package com.buildsmart.finance.repository;

import com.buildsmart.finance.entity.Expense;
import com.buildsmart.finance.entity.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String> {

    /**
     * Find all expenses for a budget
     */
    @Query("SELECT e FROM Expense e WHERE e.budgetId = :budgetId " +
            "AND e.isDeleted = false")
    Page<Expense> findByBudgetId(@Param("budgetId") String budgetId, Pageable pageable);

    /**
     * Find all expenses for a project
     */
    @Query("SELECT e FROM Expense e WHERE e.projectId = :projectId " +
            "AND e.isDeleted = false")
    Page<Expense> findByProjectId(@Param("projectId") String projectId, Pageable pageable);

    /**
     * Find all expense revisions for a parent expense (deprecated - no longer supported)
     */
    @Query("SELECT e FROM Expense e WHERE e.expenseId = :parentExpenseId AND e.isDeleted = false")
    Page<Expense> findRevisions(@Param("parentExpenseId") String parentExpenseId, Pageable pageable);

    /**
     * Find all approved expenses for a budget
     */
    @Query("SELECT e FROM Expense e WHERE e.budgetId = :budgetId AND e.status = 'APPROVED' " +
            "AND e.isDeleted = false")
    List<Expense> findApprovedExpensesByBudgetId(@Param("budgetId") String budgetId);

    /**
     * Find expenses by status
     */
    @Query("SELECT e FROM Expense e WHERE e.status = :status " +
            "AND e.isDeleted = false ORDER BY e.createdAt DESC")
    Page<Expense> findByStatus(@Param("status") ExpenseStatus status, Pageable pageable);

    /**
     * Find expenses by created user
     */
    @Query("SELECT e FROM Expense e WHERE e.createdBy = :createdBy " +
            "AND e.isDeleted = false")
    Page<Expense> findByCreatedBy(@Param("createdBy") String createdBy, Pageable pageable);

    /**
     * Find expenses for a budget created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT e FROM Expense e WHERE e.budgetId = :budgetId AND e.createdBy = :createdBy " +
            "AND e.isDeleted = false")
    Page<Expense> findByBudgetIdAndCreatedBy(@Param("budgetId") String budgetId,
                                              @Param("createdBy") String createdBy,
                                              Pageable pageable);

    /**
     * Find expenses for a project created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT e FROM Expense e WHERE e.projectId = :projectId AND e.createdBy = :createdBy " +
            "AND e.isDeleted = false")
    Page<Expense> findByProjectIdAndCreatedBy(@Param("projectId") String projectId,
                                               @Param("createdBy") String createdBy,
                                               Pageable pageable);

    /**
     * Find expenses by status created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT e FROM Expense e WHERE e.status = :status AND e.createdBy = :createdBy " +
            "AND e.isDeleted = false ORDER BY e.createdAt DESC")
    Page<Expense> findByStatusAndCreatedBy(@Param("status") ExpenseStatus status,
                                           @Param("createdBy") String createdBy,
                                           Pageable pageable);

    /**
     * Find pending expenses (not yet approved)
     */
    @Query("SELECT e FROM Expense e WHERE e.projectId = :projectId AND e.status IN ('DRAFT', 'SUBMITTED') " +
            "AND e.isDeleted = false")
    List<Expense> findPendingExpensesByProjectId(@Param("projectId") String projectId);

    /**
     * Sum of all active (non-PAID, non-deleted) expense amounts for a budget.
     * Used to calculate remaining budget before creating a new expense.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.budgetId = :budgetId " +
            "AND e.status <> com.buildsmart.finance.entity.enums.ExpenseStatus.PAID " +
            "AND e.isDeleted = false")
    java.math.BigDecimal sumActiveExpensesByBudgetId(@Param("budgetId") String budgetId);
}
