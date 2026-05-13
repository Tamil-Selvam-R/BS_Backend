package com.buildsmart.finance.entity.enums;

public enum ExpenseStatus {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    REVISION_REQUIRED("Revision Required"),
    PAID("Paid");

    private final String displayName;

    ExpenseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
