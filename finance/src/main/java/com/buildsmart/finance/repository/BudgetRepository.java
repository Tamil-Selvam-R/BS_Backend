package com.buildsmart.finance.repository;

import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.entity.enums.BudgetCategory;
import com.buildsmart.finance.entity.enums.BudgetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, String> {

    /**
     * Find the budget that resource-allocation submitted for a specific
     * (project, resource) pair. Used by the new Resource → Finance budget
     * approval flow where allocations are no longer identified by an
     * allocationId — only by (projectId, referenceResourceId).
     * Latest by createdAt so resubmissions of the same pair come first.
     */
    @Query("SELECT b FROM Budget b " +
           "WHERE b.projectId = :projectId AND b.referenceResourceId = :resourceId " +
           "AND b.isDeleted = false " +
           "ORDER BY b.createdAt DESC")
    List<Budget> findLatestByProjectIdAndResourceId(
            @Param("projectId") String projectId,
            @Param("resourceId") String resourceId);

    /**
     * Find budget by project ID and category
     */
    @Query("SELECT b FROM Budget b WHERE b.projectId = :projectId AND b.budgetCategory = :category " +
            "AND b.isDeleted = false")
    Optional<Budget> findByProjectIdAndCategory(
            @Param("projectId") String projectId,
            @Param("category") BudgetCategory category);

    /**
     * Find all budgets for a project (with pagination and search)
     */
    @Query("SELECT b FROM Budget b WHERE b.projectId = :projectId " +
            "AND b.isDeleted = false")
    Page<Budget> findByProjectId(@Param("projectId") String projectId, Pageable pageable);

    /**
     * Find all budget revisions for a parent budget (deprecated - no longer supported)
     */
    @Query("SELECT b FROM Budget b WHERE b.budgetId = :parentBudgetId AND b.isDeleted = false")
    Page<Budget> findRevisions(@Param("parentBudgetId") String parentBudgetId, Pageable pageable);

    /**
     * Find all approved budgets for a project
     */
    @Query("SELECT b FROM Budget b WHERE b.projectId = :projectId AND b.status = 'APPROVED' " +
            "AND b.isDeleted = false")
    List<Budget> findApprovedBudgetsByProjectId(@Param("projectId") String projectId);

    /**
     * Find budgets by status
     */
    @Query("SELECT b FROM Budget b WHERE b.status = :status " +
            "AND b.isDeleted = false ORDER BY b.createdAt DESC")
    Page<Budget> findByStatus(@Param("status") BudgetStatus status, Pageable pageable);

    /**
     * Check if budget exists for project and category
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
            "FROM Budget b WHERE b.projectId = :projectId AND b.budgetCategory = :category " +
            "AND b.isDeleted = false")
    boolean existsByProjectIdAndCategoryAndIsDeletedFalse(String projectId, BudgetCategory category);
    /**
     * Find budgets created by user
     */
    @Query("SELECT b FROM Budget b WHERE b.createdBy = :createdBy " +
            "AND b.isDeleted = false")
    Page<Budget> findByCreatedBy(@Param("createdBy") String createdBy, Pageable pageable);

    /**
     * Find budgets for a project created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT b FROM Budget b WHERE b.projectId = :projectId AND b.createdBy = :createdBy " +
            "AND b.isDeleted = false")
    Page<Budget> findByProjectIdAndCreatedBy(@Param("projectId") String projectId,
                                              @Param("createdBy") String createdBy,
                                              Pageable pageable);

    /**
     * Find budgets by status created by a specific user (FINANCE_OFFICER scoping).
     */
    @Query("SELECT b FROM Budget b WHERE b.status = :status AND b.createdBy = :createdBy " +
            "AND b.isDeleted = false ORDER BY b.createdAt DESC")
    Page<Budget> findByStatusAndCreatedBy(@Param("status") BudgetStatus status,
                                          @Param("createdBy") String createdBy,
                                          Pageable pageable);

    /**
     * Find all active budgets for a project — used for utilization summary.
     */
    @Query("SELECT b FROM Budget b WHERE b.projectId = :projectId AND b.isDeleted = false ORDER BY b.createdAt ASC")
    List<Budget> findAllActiveByProjectId(@Param("projectId") String projectId);
}
