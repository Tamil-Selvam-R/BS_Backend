package com.company.notification.exception;

/**
 * Thrown by service fallbacks when the primary path is unavailable
 * (DB down, circuit open, timeout). Mapped to HTTP 503 by the global handler.
 */
public class ServiceDegradedException extends RuntimeException {
    public ServiceDegradedException(String message) {
        super(message);
    }

    public ServiceDegradedException(String message, Throwable cause) {
        super(message, cause);
    }
}
