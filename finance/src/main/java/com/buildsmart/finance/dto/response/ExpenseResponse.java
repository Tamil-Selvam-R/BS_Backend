package com.buildsmart.finance.dto.response;

import com.buildsmart.finance.entity.enums.ExpenseStatus;
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
public class ExpenseResponse {

    private String expenseId;
    private String projectId;
    private String budgetId;
    private String expenseType;
    private String invoiceId;
    private String description;
    private BigDecimal amount;
    private LocalDateTime expenseDate;
    private ExpenseStatus status;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** How much budget is still available after this expense. Populated on create; null on list queries. */
    private BigDecimal remainingBudget;
}
