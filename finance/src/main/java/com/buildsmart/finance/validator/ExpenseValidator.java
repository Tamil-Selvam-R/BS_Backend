package com.buildsmart.finance.validator;

import com.buildsmart.finance.dto.request.ExpenseCreateRequest;
import com.buildsmart.finance.dto.request.ExpenseUpdateRequest;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.entity.Expense;
import com.buildsmart.finance.exception.BusinessRuleException;
import com.buildsmart.finance.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpenseValidator {

    /**
     * Validate expense creation.
     * Checks budget is APPROVED and the new expense amount fits within remaining budget.
     * alreadySpent = sum of all non-PAID active expenses for this budget.
     */
    public void validateExpenseCreation(ExpenseCreateRequest request, Budget budget, BigDecimal alreadySpent) {
        log.info("Validating expense creation for budget: {}", request.getBudgetId());

        if (!budget.getStatus().name().equals("APPROVED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-005",
                    "Budget must be APPROVED before adding expenses. Current status: " + budget.getStatus()
            );
        }

        BigDecimal remaining = budget.getPlannedAmount().subtract(alreadySpent);
        if (request.getAmount() != null && request.getAmount().compareTo(remaining) > 0) {
            throw new BusinessRuleException(
                    "FIN-BUS-011",
                    "Expense amount " + request.getAmount() +
                            " exceeds remaining budget " + remaining +
                            " (planned: " + budget.getPlannedAmount() +
                            ", already spent: " + alreadySpent + ")"
            );
        }
    }

    /**
     * Overload used when amount is already resolved from invoice (non-LABOUR flow).
     */
    public void validateExpenseCreation(Budget budget, BigDecimal expenseAmount, BigDecimal alreadySpent) {
        log.info("Validating expense amount {} against budget {}", expenseAmount, budget.getBudgetId());

        if (!budget.getStatus().name().equals("APPROVED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-005",
                    "Budget must be APPROVED before adding expenses. Current status: " + budget.getStatus()
            );
        }

        BigDecimal remaining = budget.getPlannedAmount().subtract(alreadySpent);
        if (expenseAmount.compareTo(remaining) > 0) {
            throw new BusinessRuleException(
                    "FIN-BUS-011",
                    "Expense amount " + expenseAmount +
                            " exceeds remaining budget " + remaining +
                            " (planned: " + budget.getPlannedAmount() +
                            ", already spent: " + alreadySpent + ")"
            );
        }
    }

    /**
     * Kept for PaymentValidator compatibility — expense must be APPROVED or PAID to be paid.
     */
    public void validateExpenseIsApproved(String expenseStatus) {
        if (!expenseStatus.equals("APPROVED") && !expenseStatus.equals("PAID")) {
            throw new BusinessRuleException(
                    "FIN-BUS-007",
                    "Expense must be APPROVED for payment processing. Current status: " + expenseStatus
            );
        }
    }

    /**
     * Validate expense update request fields.
     */
    public void validateExpenseUpdate(ExpenseUpdateRequest request) {
        log.info("Validating expense update request");

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("FIN-VAL-013", "amount", "Amount must be greater than 0");
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new ValidationException("FIN-VAL-014", "description", "Description is required");
        }

        if (request.getExpenseDate() == null) {
            throw new ValidationException("FIN-VAL-015", "expenseDate", "Expense date is required");
        }
    }

    /**
     * Only APPROVED expenses may be edited. PAID expenses are locked.
     */
    public void validateExpenseCanBeEdited(Expense expense) {
        String s = expense.getStatus().name();
        if (!s.equals("APPROVED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-008",
                    "Only APPROVED expenses can be edited. Current status: " + s
            );
        }
    }

    /**
     * Only APPROVED expenses may be deleted. PAID expenses cannot be deleted.
     */
    public void validateExpenseCanBeDeleted(Expense expense) {
        String s = expense.getStatus().name();
        if (!s.equals("APPROVED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-009",
                    "Only APPROVED expenses can be deleted. Current status: " + s +
                            ". PAID expenses cannot be deleted."
            );
        }
    }
}
