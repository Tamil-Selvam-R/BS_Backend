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
public class BudgetUtilizationResponse {

    private String budgetId;
    private String projectId;
    private String taskId;
    private BudgetCategory budgetCategory;
    private BudgetStatus status;

    private BigDecimal plannedAmount;
    private BigDecimal actualAmount;
    private BigDecimal remainingAmount;
    private BigDecimal variance;
    private BigDecimal utilizationPercentage;

    private boolean overBudget;
    private int completedPaymentsCount;

    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
