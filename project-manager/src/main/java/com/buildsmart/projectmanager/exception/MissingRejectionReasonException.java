package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class MissingRejectionReasonException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_REJECTION_REASON_REQUIRED";

    public MissingRejectionReasonException() {
        super(
            ERROR_CODE,
            "Rejection reason is required when rejecting an approval request.",
            "When rejecting a request, a detailed reason must be provided to inform the requesting user.",
            HttpStatus.BAD_REQUEST
        );
    }
}
