package com.buildsmart.safety.web;

import com.buildsmart.safety.common.exception.DuplicateResourceException;
import com.buildsmart.safety.common.exception.ResourceNotFoundException;
import com.buildsmart.safety.exception.InvalidStatusTransitionException;
import com.buildsmart.safety.exception.ProjectNotAvailableException;
import com.buildsmart.safety.exception.TaskAlreadyCompletedException;
import com.buildsmart.safety.exception.TaskNotAssignedToOfficerException;
import com.buildsmart.safety.exception.UnauthorizedOperationException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = "Invalid value provided.";
        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife
                && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String validValues = java.util.Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            message = "Invalid value '" + ife.getValue() + "'. Allowed values: " + validValues;
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_ENUM_VALUE", message);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleStatusTransition(InvalidStatusTransitionException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATUS_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedOperationException ex) {
        return error(HttpStatus.FORBIDDEN, "UNAUTHORIZED_OPERATION", ex.getMessage());
    }

    @ExceptionHandler(ProjectNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleProjectNotAvailable(ProjectNotAvailableException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "PROJECT_NOT_AVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(TaskNotAssignedToOfficerException.class)
    public ResponseEntity<Map<String, Object>> handleTaskNotAssigned(TaskNotAssignedToOfficerException ex) {
        return error(HttpStatus.FORBIDDEN, "TASK_NOT_ASSIGNED_TO_OFFICER", ex.getMessage());
    }

    @ExceptionHandler(TaskAlreadyCompletedException.class)
    public ResponseEntity<Map<String, Object>> handleTaskAlreadyCompleted(TaskAlreadyCompletedException ex) {
        return error(HttpStatus.CONFLICT, "TASK_ALREADY_COMPLETED", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<Map<String, Object>> handleFeignNotFound(FeignException.NotFound ex) {
        return error(HttpStatus.NOT_FOUND, "DOWNSTREAM_NOT_FOUND",
                "A required resource was not found in the downstream service.");
    }

    @ExceptionHandler(FeignException.Forbidden.class)
    public ResponseEntity<Map<String, Object>> handleFeignForbidden(FeignException.Forbidden ex) {
        return error(HttpStatus.FORBIDDEN, "DOWNSTREAM_FORBIDDEN",
                "The downstream service rejected the request — check role permissions.");
    }

    @ExceptionHandler(FeignException.Unauthorized.class)
    public ResponseEntity<Map<String, Object>> handleFeignUnauthorized(FeignException.Unauthorized ex) {
        return error(HttpStatus.UNAUTHORIZED, "DOWNSTREAM_UNAUTHORIZED",
                "The downstream service rejected the token. Ensure the JWT secret matches.");
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeign(FeignException ex) {
        // Show real HTTP status and body so debugging is easier
        int status = ex.status();
        String body = ex.contentUTF8();
        String message = "Downstream call failed (HTTP " + status + ")"
                + (body != null && !body.isBlank() ? ": " + body : "");
        return error(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error: " + ex.getClass().getSimpleName() + " — " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", code,
                "message", message));
    }
}
