package com.buildsmart.finance.entity.enums;

public enum PaymentStatus {
    INITIATED("Initiated"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    REJECTED("Rejected");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
