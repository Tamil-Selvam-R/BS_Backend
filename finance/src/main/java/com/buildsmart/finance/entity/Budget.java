package com.buildsmart.finance.entity;

import com.buildsmart.finance.entity.enums.BudgetCategory;
import com.buildsmart.finance.entity.enums.BudgetStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "budgets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "budget_category"},
                name = "uk_project_category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @Column(name = "budget_id", length = 20)
    private String budgetId;

    @Column(name = "project_id", length = 20, nullable = false)
    private String projectId;

    @Column(name = "task_id", length = 50)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_category", nullable = false)
    private BudgetCategory budgetCategory;

    @Column(name = "planned_amount", nullable = false)
    private BigDecimal plannedAmount;

    @Column(name = "actual_amount")
    private BigDecimal actualAmount;

    @Column(name = "variance")
    private BigDecimal variance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BudgetStatus status;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * Rejection remarks from the Project Manager when status = REJECTED.
     * FEATURE SET 4 (common approval engine) — rejection must always store remarks.
     * Nullable so existing APPROVED budgets remain valid.
     */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * Optional — set when this budget was created in response to a Resource
     * Allocation request. When the budget is approved/rejected we use this
     * id to call resource-allocation back so it can auto-create the allocation.
     */
    @Column(name = "reference_resource_id", length = 30)
    private String referenceResourceId;

    /**
     * Calculate variance: planned_amount - actual_amount
     */
    public void calculateVariance() {
        if (this.actualAmount != null) {
            this.variance = this.plannedAmount.subtract(this.actualAmount);
        }
    }

    /**
     * Check if budget is within ±5% threshold
     */
    public boolean isNearBudget() {
        if (this.variance == null) {
            return true;
        }
        BigDecimal threshold = this.plannedAmount.multiply(new BigDecimal("0.05"));
        return this.variance.abs().compareTo(threshold) <= 0;
    }

    /**
     * Check if budget is over budget (variance < 0)
     */
    public boolean isOverBudget() {
        return this.variance != null && this.variance.compareTo(BigDecimal.ZERO) < 0;
    }
}
