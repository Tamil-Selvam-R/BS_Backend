package com.buildsmart.finance.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {

    private static final AtomicLong budgetCounter = new AtomicLong(0);
    private static final AtomicLong expenseCounter = new AtomicLong(0);
    private static final AtomicLong paymentCounter = new AtomicLong(0);

    /**
     * Generate Budget ID: BUD-YYYYMMDDHH-SEQUENCE
     */
    public static synchronized String generateBudgetId() {
        long counter = budgetCounter.incrementAndGet();
        String timestamp = String.format("%d", Instant.now().toEpochMilli() / 1000);
        return String.format("BUD-%s-%05d", timestamp.substring(timestamp.length() - 8), counter % 100000);
    }

    /**
     * Generate Expense ID: EXP-YYYYMMDDHH-SEQUENCE
     */
    public static synchronized String generateExpenseId() {
        long counter = expenseCounter.incrementAndGet();
        String timestamp = String.format("%d", Instant.now().toEpochMilli() / 1000);
        return String.format("EXP-%s-%05d", timestamp.substring(timestamp.length() - 8), counter % 100000);
    }

    /**
     * Generate Payment ID: PAY-YYYYMMDDHH-SEQUENCE
     */
    public static synchronized String generatePaymentId() {
        long counter = paymentCounter.incrementAndGet();
        String timestamp = String.format("%d", Instant.now().toEpochMilli() / 1000);
        return String.format("PAY-%s-%05d", timestamp.substring(timestamp.length() - 8), counter % 100000);
    }

    /** e.g. TSKFN001, TSKFN002 — local AssignedTask IDs synced from PM */
    public static String nextAssignedTaskId(String lastAssignedTaskId) {
        if (lastAssignedTaskId == null || lastAssignedTaskId.length() < 3) {
            return "TSKFN001";
        }
        try {
            String numericPart = lastAssignedTaskId.substring(lastAssignedTaskId.length() - 3);
            int next = Integer.parseInt(numericPart) + 1;
            return String.format("TSKFN%03d", next);
        } catch (NumberFormatException ex) {
            return "TSKFN001";
        }
    }
}
