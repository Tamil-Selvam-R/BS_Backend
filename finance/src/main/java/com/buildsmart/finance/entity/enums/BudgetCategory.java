package com.buildsmart.finance.entity.enums;

public enum BudgetCategory {
    MATERIAL("Material"),
    LABOR("Labor"),
    EQUIPMENT("Equipment"),
    SUBCONTRACT("Subcontract"),
    MISCELLANEOUS("Miscellaneous");

    private final String displayName;

    BudgetCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
