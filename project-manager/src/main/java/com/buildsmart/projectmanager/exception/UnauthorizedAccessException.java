package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedAccessException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_UNAUTHORIZED";

    public UnauthorizedAccessException(String resource) {
        super(
            ERROR_CODE,
            String.format("Unauthorized access to %s.", resource),
            String.format("Access to %s is restricted. Please ensure you have the appropriate permissions.", resource),
            HttpStatus.FORBIDDEN
        );
    }

    public UnauthorizedAccessException(String resource, String userId) {
        super(
            ERROR_CODE,
            String.format("Unauthorized access to %s.", resource),
            String.format("User %s does not have permission to access %s. Access is restricted based on role and department.", userId, resource),
            HttpStatus.FORBIDDEN
        );
    }
}
