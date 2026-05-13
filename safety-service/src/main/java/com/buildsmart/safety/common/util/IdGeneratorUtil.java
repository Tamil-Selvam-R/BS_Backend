package com.buildsmart.safety.common.util;

public class IdGeneratorUtil {

    private IdGeneratorUtil() {}


    public static String nextIncidentId(String lastId) {
        if (lastId == null || lastId.isBlank()) return "INC001";
        try {
            int num = Integer.parseInt(lastId.replace("INC", ""));
            return String.format("INC%03d", num + 1);
        } catch (NumberFormatException e) {
            return "INC001";
        }
    }

    
    public static String nextInspectionId(String lastId) {
        if (lastId == null || lastId.isBlank()) return "INS001";
        try {
            int num = Integer.parseInt(lastId.replace("INS", ""));
            return String.format("INS%03d", num + 1);
        } catch (NumberFormatException e) {
            return "INS001";
        }
    }

  
    public static String nextNotificationId(String lastId) {
        if (lastId == null || lastId.isBlank()) return "NTF001";
        try {
            int num = Integer.parseInt(lastId.replace("NTF", ""));
            return String.format("NTF%03d", num + 1);
        } catch (NumberFormatException e) {
            return "NTF001";
        }
    }

    /**
     * Generates the next AssignedTask local ID (e.g. SAT001, SAT002 ...).
     */
    public static String nextAssignedTaskId(String lastId) {
        if (lastId == null || lastId.isBlank()) return "SAT001";
        try {
            int num = Integer.parseInt(lastId.replace("SAT", ""));
            return String.format("SAT%03d", num + 1);
        } catch (NumberFormatException e) {
            return "SAT001";
        }
    }
}
