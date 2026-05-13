package com.buildsmart.projectmanager.entity;

public enum NotificationType {
    TASK_ASSIGNED("Task Assigned"),
    APPROVAL_REQUIRED("Approval Required"),
    APPROVAL_ACCEPTED("Approval Accepted"),
    APPROVAL_REJECTED("Approval Rejected"),
    MILESTONE_UPDATE("Milestone Update"),
    PROJECT_UPDATE("Project Update");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
