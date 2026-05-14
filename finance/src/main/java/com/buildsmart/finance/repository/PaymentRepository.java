package com.buildsmart.finance.repository;

import com.buildsmart.finance.entity.Payment;
import com.buildsmart.finance.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    /**
     * Find payment by invoice ID and amount (idempotency check)
     */
    @Query("SELECT p FROM Payment p WHERE p.invoiceId = :invoiceId AND p.amount = :amount " +
            "AND p.isDeleted = false")
    Optional<Payment> findByInvoiceIdAndAmount(
            @Param("invoiceId") String invoiceId,
            @Param("amount") BigDecimal amount);

    /**
     * Find any active (non-rejected) payment for an invoice.
     * Used to prevent double-payment regardless of amount.
     */
    @Query("SELECT p FROM Payment p WHERE p.invoiceId = :invoiceId " +
            "AND p.status != com.buildsmart.finance.entity.enums.PaymentStatus.REJECTED " +
            "AND p.isDeleted = false")
    Optional<Payment> findActivePaymentByInvoiceId(@Param("invoiceId") String invoiceId);

    /**
     * Find payments linked to a specific budget directly.
     */
    @Query("SELECT p FROM Payment p WHERE p.budgetId = :budgetId AND p.isDeleted = false")
    Page<Payment> findByBudgetId(@Param("budgetId") String budgetId, Pageable pageable);

    /**
     * Find all payments for an expense
     */
    @Query("SELECT p FROM Payment p WHERE p.expenseId = :expenseId AND p.isDeleted = false")
    Page<Payment> findByExpenseId(@Param("expenseId") String expenseId, Pageable pageable);

    /**
     * Find all payments by status
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.isDeleted = false")
    Page<Payment> findByStatus(@Param("status") PaymentStatus status, Pageable pageable);

    /**
     * Find completed payments for a budget (to update actual amount)
     */
    @Query("SELECT p FROM Payment p WHERE p.expenseId IN " +
            "(SELECT e.expenseId FROM Expense e WHERE e.budgetId = :budgetId) " +
            "AND p.status = 'COMPLETED' AND p.isDeleted = false")
    List<Payment> findCompletedPaymentsByBudgetId(@Param("budgetId") String budgetId);

    /**
     * Find payments by created user
     */
    @Query("SELECT p FROM Payment p WHERE p.createdBy = :createdBy AND p.isDeleted = false")
    Page<Payment> findByCreatedBy(@Param("createdBy") String createdBy, Pageable pageable);

    /**
     * Find payments for an expense created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT p FROM Payment p WHERE p.expenseId = :expenseId AND p.createdBy = :createdBy AND p.isDeleted = false")
    Page<Payment> findByExpenseIdAndCreatedBy(@Param("expenseId") String expenseId,
                                              @Param("createdBy") String createdBy,
                                              Pageable pageable);

    /**
     * Find payments by status created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdBy = :createdBy AND p.isDeleted = false")
    Page<Payment> findByStatusAndCreatedBy(@Param("status") PaymentStatus status,
                                           @Param("createdBy") String createdBy,
                                           Pageable pageable);

    /**
     * Find pending payments created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('INITIATED', 'PENDING') " +
            "AND p.createdBy = :createdBy AND p.isDeleted = false")
    List<Payment> findPendingPaymentsByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Find pending payments
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('INITIATED', 'PENDING') AND p.isDeleted = false")
    List<Payment> findPendingPayments();

    /**
     * Check if payment exists for invoice
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM Payment p WHERE p.invoiceId = :invoiceId AND p.isDeleted = false")
    boolean existsByInvoiceId(@Param("invoiceId") String invoiceId);
}
