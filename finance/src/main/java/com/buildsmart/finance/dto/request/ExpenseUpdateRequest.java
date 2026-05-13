package com.buildsmart.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Expense Update Request DTO
 * Only allows updating description, amount, and expenseDate
 * Cannot update budgetId, status, or other immutable fields
 * Only valid for DRAFT status expenses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseUpdateRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

}
