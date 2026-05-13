package com.buildsmart.safety.exception;

public class TaskNotAssignedToOfficerException extends RuntimeException {
    public TaskNotAssignedToOfficerException(String taskId, String officerId) {
        super("Task " + taskId + " is not assigned to officer " + officerId + " or does not belong to the given project.");
    }
}
