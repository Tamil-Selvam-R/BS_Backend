package com.buildsmart.projectmanager.entity;

public enum TaskStatus {
    PENDING("Pending"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    SUBMITTED("Submitted"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    BLOCKED("Blocked"),
    AWAITING_APPROVAL("Awaiting Approval");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
