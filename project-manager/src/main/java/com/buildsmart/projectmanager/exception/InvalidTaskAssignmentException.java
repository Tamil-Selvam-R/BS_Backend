package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class InvalidTaskAssignmentException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_INVALID_TASK_ASSIGNMENT";

    public InvalidTaskAssignmentException(String message, String details) {
        super(ERROR_CODE, message, details, HttpStatus.BAD_REQUEST);
    }
}

