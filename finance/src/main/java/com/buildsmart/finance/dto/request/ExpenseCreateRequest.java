package com.buildsmart.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class ExpenseCreateRequest {

    @NotBlank(message = "Project ID is required")
    private String projectId;

    @NotBlank(message = "Budget ID is required")
    private String budgetId;

    @NotBlank(message = "Expense type is required")
    private String expenseType;

    private String description;

    /**
     * Required for LABOUR (manual entry).
     * For other types (MATERIAL, EQUIPMENT, OVERHEAD, CONTINGENCY), if invoiceId
     * is provided the amount is fetched from the vendor invoice — this field is ignored.
     */
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    /**
     * Optional for LABOUR; required for all other expense types.
     * When present, the expense amount is taken directly from the APPROVED vendor invoice.
     */
    private String invoiceId;

    @NotNull(message = "Expense date is required")
    private LocalDateTime expenseDate;
}
