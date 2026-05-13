package com.buildsmart.finance.exception;

public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;
    private final String resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = "FIN-NOT-FOUND-001";
        this.resourceId = null;
    }

    public ResourceNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String errorCode, String resourceId, String message) {
        super(message);
        this.errorCode = errorCode;
        this.resourceId = resourceId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getResourceId() {
        return resourceId;
    }
}
