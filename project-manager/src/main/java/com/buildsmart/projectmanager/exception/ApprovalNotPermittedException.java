package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class ApprovalNotPermittedException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_APPROVAL_DENIED";

    public ApprovalNotPermittedException(String userId, String action) {
        super(
            ERROR_CODE,
            "You do not have permission to perform this approval action.",
            String.format("User %s attempted to %s but does not have the required Project Manager role.", userId, action),
            HttpStatus.FORBIDDEN
        );
    }
}
