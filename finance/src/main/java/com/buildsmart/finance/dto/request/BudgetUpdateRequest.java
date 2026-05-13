package com.buildsmart.finance.dto.request;

import com.buildsmart.finance.entity.enums.BudgetCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Budget Update Request DTO
 * Only allows updating plannedAmount and budgetCategory
 * Cannot update projectId, status, or other immutable fields
 * Only valid for DRAFT status budgets
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetUpdateRequest {

    @NotNull(message = "Budget category is required")
    private BudgetCategory budgetCategory;

    @NotNull(message = "Planned amount is required")
    @DecimalMin(value = "0.01", message = "Planned amount must be greater than 0")
    private BigDecimal plannedAmount;

}
