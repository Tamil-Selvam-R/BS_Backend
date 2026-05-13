package com.buildsmart.safety.domain.model;

public enum AssignedTaskStatus {
    PENDING("Pending"),
    /** Submitted to the Project Manager and awaiting approval. */
    SUBMITTED("Submitted"),
    /** Approved by PM — terminal "completed" state. */
    COMPLETED("Completed"),
    /** Rejected by PM — counts as "not completed"; the officer can rework and resubmit. */
    REJECTED("Rejected");

    private final String displayName;

    AssignedTaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
