package com.buildsmart.safety.exception;

public class ProjectNotAvailableException extends RuntimeException {
    public ProjectNotAvailableException(String projectId, String status) {
        super("Project " + projectId + " is not available for inspection. Current status: " + status);
    }
}
