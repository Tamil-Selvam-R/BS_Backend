package com.buildsmart.safety.exception;

public class TaskAlreadyCompletedException extends RuntimeException {
    public TaskAlreadyCompletedException(String taskId) {
        super("Task " + taskId + " has already been completed by a previous inspection.");
    }
}
