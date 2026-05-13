package com.buildsmart.resource_allocation.utility;


public final class IdGeneratorUtil {

    private IdGeneratorUtil() {
    }

    public static String nextResourceId(String lastResourceId) {
        int next = extractNumericSuffix(lastResourceId, 3) + 1;
        return String.format("RESBS%03d", next);
    }

    public static String nextAllocationId(String lastAllocationId) {
        int next = extractNumericSuffix(lastAllocationId, 3) + 1;
        return String.format("ALCBS%03d", next);
    }

    public static String nextNotificationId(String lastNotificationId) {
        int next = extractNumericSuffix(lastNotificationId, 3) + 1;
        return String.format("NOTBS%03d", next);
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

