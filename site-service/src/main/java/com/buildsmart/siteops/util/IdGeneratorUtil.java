package com.buildsmart.siteops.util;

public final class IdGeneratorUtil {

    private IdGeneratorUtil() {
    }

    /** e.g. LOGBS001, LOGBS002 */
    public static String nextSiteLogId(String lastSiteLogId) {
        int next = extractNumericSuffix(lastSiteLogId, 3) + 1;
        return String.format("LOGBS%03d", next);
    }

    /** e.g. ISSBS001, ISSBS002 */
    public static String nextIssueId(String lastIssueId) {
        int next = extractNumericSuffix(lastIssueId, 3) + 1;
        return String.format("ISSBS%03d", next);
    }

    /**
     * Approval IDs for site-log / issue approvals sent to PM.
     * Format: APRSE001, APRSE002  (SE = Site Engineer)
     */
    public static String nextApprovalId(String lastApprovalId) {
        int next = extractNumericSuffix(lastApprovalId, 3) + 1;
        return String.format("APRSE%03d", next);
    }


    /** e.g. NOTBS001, NOTBS002 */
    public static String nextNotificationId(String lastNotificationId) {
        int next = extractNumericSuffix(lastNotificationId, 3) + 1;
        return String.format("NOTBS%03d", next);
    }


    /** e.g. TSKBS001, TSKBS002 — local AssignedTask IDs synced from PM */
    public static String nextAssignedTaskId(String lastAssignedTaskId) {
        int next = extractNumericSuffix(lastAssignedTaskId, 3) + 1;
        return String.format("TSKBS%03d", next);
    }


    private static int extractNumericSuffix(String id, int digits) {
        if (id == null || id.length() < digits) {
            return 0;
        }
        String suffix = id.substring(id.length() - digits);
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

