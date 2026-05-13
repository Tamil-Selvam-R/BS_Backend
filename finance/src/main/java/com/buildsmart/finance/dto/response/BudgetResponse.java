package com.buildsmart.finance.dto.response;

import com.buildsmart.finance.entity.enums.BudgetCategory;
import com.buildsmart.finance.entity.enums.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponse {

    private String budgetId;
    private String projectId;
    private String taskId;
    private BudgetCategory budgetCategory;
    private BigDecimal plannedAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BudgetStatus status;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Project-level budget snapshot — populated on create/update so the caller
    // can see how much of the PM's overall budget is still available.
    private BigDecimal projectTotalBudget;
    private BigDecimal projectAllocatedBudget;
    private BigDecimal projectRemainingBudget;
    private String allocationWarning;
}
