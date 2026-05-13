package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_NOT_FOUND";

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(
            ERROR_CODE,
            String.format("%s \"%s\" not found.", resourceType, resourceId),
            String.format("No %s exists with ID %s. Please verify the ID.", resourceType.toLowerCase(), resourceId),
            HttpStatus.NOT_FOUND
        );
    }
}
