package com.buildsmart.finance.entity.enums;

public enum BudgetStatus {
    DRAFT("Draft"),
    /**
     * FEATURE SET 3 + 4 — Common approval engine.
     * Budget has been submitted to the Project Manager and is awaiting decision.
     */
    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    /** Optional terminal state once an approved budget has been fully consumed. */
    COMPLETED("Completed");

    private final String displayName;

    BudgetStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
