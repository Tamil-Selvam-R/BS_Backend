package com.buildsmart.projectmanager.entity;

public enum ApprovalType {
    BUDGET("Budget"),
    VENDOR("Vendor"),
    SITE_WORK("Site Work"),
    SAFETY("Safety");

    private final String displayName;

    ApprovalType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
