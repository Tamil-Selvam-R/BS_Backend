package com.buildsmart.finance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectBudgetUtilizationResponse {

    private String projectId;

    private BigDecimal totalPlannedAmount;
    private BigDecimal totalActualAmount;
    private BigDecimal totalRemainingAmount;
    private BigDecimal overallUtilizationPercentage;

    private int totalBudgets;
    private int approvedBudgets;
    private int overBudgetCount;

    private List<BudgetUtilizationResponse> budgets;
}
