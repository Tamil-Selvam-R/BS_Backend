package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class DuplicateApprovalIdException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_DUP_APPROVAL";

    public DuplicateApprovalIdException(String approvalId) {
        super(
            ERROR_CODE,
            String.format("Approval ID \"%s\" already exists. Please use a different approval ID.", approvalId),
            String.format("An approval request with ID %s is already registered in the system.", approvalId),
            HttpStatus.CONFLICT
        );
    }
}

