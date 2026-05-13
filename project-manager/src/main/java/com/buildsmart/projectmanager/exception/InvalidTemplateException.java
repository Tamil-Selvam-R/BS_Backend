package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

public class InvalidTemplateException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_INVALID_TEMPLATE";

    public InvalidTemplateException(String templateId) {
        super(
            ERROR_CODE,
            String.format("Invalid template selection: \"%s\".", templateId),
            String.format("The template ID %s is not a valid template. Please select from available templates: TEMPBS01, TEMPBS02, TEMPBS03, TEMPBS04.", templateId),
            HttpStatus.BAD_REQUEST
        );
    }
}
