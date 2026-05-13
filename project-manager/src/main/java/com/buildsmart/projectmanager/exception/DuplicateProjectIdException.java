package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class DuplicateProjectIdException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_DUP_PROJECT";

    public DuplicateProjectIdException(String projectId) {
        super(
            ERROR_CODE,
            String.format("Project ID \"%s\" already exists. Please use a different project ID.", projectId),
            String.format("A project with ID %s is already registered in the system. Project IDs must be unique.", projectId),
            HttpStatus.CONFLICT
        );
    }
}
