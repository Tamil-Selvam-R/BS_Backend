package com.buildsmart.finance.entity.enums;

public enum PaymentMethod {
    BANK_TRANSFER("Bank Transfer"),
    CHEQUE("Cheque");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
