package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTaskIdException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_DUP_TASK";

    public DuplicateTaskIdException(String taskId) {
        super(
            ERROR_CODE,
            String.format("Task ID \"%s\" already exists. Please use a different task ID.", taskId),
            String.format("A task with ID %s is already registered in the system. Task IDs must be unique.", taskId),
            HttpStatus.CONFLICT
        );
    }
}
