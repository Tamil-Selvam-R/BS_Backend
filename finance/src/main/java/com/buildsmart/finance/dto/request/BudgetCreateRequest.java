package com.buildsmart.finance.dto.request;

import com.buildsmart.finance.entity.enums.BudgetCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCreateRequest {

    @NotBlank(message = "Project ID is required")
    private String projectId;

    @NotBlank(message = "Task ID is required")
    private String taskId;

    @NotNull(message = "Budget category is required")
    private BudgetCategory budgetCategory;

    @NotNull(message = "Planned amount is required")
    @Positive(message = "Planned amount must be greater than 0")
    private BigDecimal plannedAmount;
}
