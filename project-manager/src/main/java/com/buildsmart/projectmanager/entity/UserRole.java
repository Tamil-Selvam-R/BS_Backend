package com.buildsmart.projectmanager.entity;

public enum UserRole {
    ADMIN("Admin"),
    PROJECT_MANAGER("Project Manager"),
    FINANCE_OFFICER("Finance Officer"),
    VENDOR("Vendor"),
    SITE_ENGINEER("Site Engineer"),
    SAFETY_OFFICER("Safety Officer");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DepartmentCode getDepartmentCode() {
        return switch (this) {
            case ADMIN -> DepartmentCode.ADMIN;
            case PROJECT_MANAGER -> DepartmentCode.PROJECT_MANAGER;
            case FINANCE_OFFICER -> DepartmentCode.FINANCE_OFFICER;
            case VENDOR -> DepartmentCode.VENDOR;
            case SITE_ENGINEER -> DepartmentCode.SITE_ENGINEER;
            case SAFETY_OFFICER -> DepartmentCode.SAFETY_OFFICER;
        };
    }
}
