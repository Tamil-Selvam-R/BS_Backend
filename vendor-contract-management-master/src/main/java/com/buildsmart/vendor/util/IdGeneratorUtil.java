package com.buildsmart.vendor.util;


public final class IdGeneratorUtil {

    private IdGeneratorUtil() {
        // prevent instantiation
    }

    public static String nextVendorId(String lastVendorId) {
        int next = extractNumericSuffix(lastVendorId, 3) + 1;
        return String.format("BSVM%03d", next);
    }

    public static String nextContractId(String lastContractId) {
        int next = extractNumericSuffix(lastContractId, 3) + 1;
        return String.format("CONBS%03d", next);
    }

    public static String nextDeliveryId(String lastDeliveryId) {
        int next = extractNumericSuffix(lastDeliveryId, 3) + 1;
        return String.format("DELBS%03d", next);
    }

    public static String nextInvoiceId(String lastInvoiceId) {
        int next = extractNumericSuffix(lastInvoiceId, 3) + 1;
        return String.format("INVBS%03d", next);
    }

    public static String nextDocumentId(String lastDocumentId) {
        int next = extractNumericSuffix(lastDocumentId, 3) + 1;
        return String.format("DOCBS%03d", next);
    }

    public static String nextApprovalId(String lastApprovalId) {
        int next = extractNumericSuffix(lastApprovalId, 3) + 1;
        return String.format("APRVN%03d", next);
    }

    /** e.g. TSKVN001, TSKVN002 — local AssignedTask IDs synced from PM */
    public static String nextAssignedTaskId(String lastAssignedTaskId) {
        int next = extractNumericSuffix(lastAssignedTaskId, 3) + 1;
        return String.format("TSKVN%03d", next);
    }

    
    
    private static int extractNumericSuffix(String id, int length) {
        if (id == null || id.length() < length) {
            return 0;
        }

        try {
            String numericPart = id.substring(id.length() - length);
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}