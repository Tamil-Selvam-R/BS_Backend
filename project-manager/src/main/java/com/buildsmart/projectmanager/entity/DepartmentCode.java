package com.buildsmart.projectmanager.entity;

public enum DepartmentCode {
    ADMIN("Admin"),
    PROJECT_MANAGER("Project Manager"),
    SITE_ENGINEER("Site Engineer"),
    SAFETY_OFFICER("Safety Officer"),
    VENDOR("Vendor"),
    FINANCE_OFFICER("Finance Officer");

    private final String displayName;

    DepartmentCode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
