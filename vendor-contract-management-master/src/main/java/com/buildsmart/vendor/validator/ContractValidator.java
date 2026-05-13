package com.buildsmart.vendor.validator;

import com.buildsmart.vendor.dto.request.ContractRequest;
import com.buildsmart.vendor.exception.CustomExceptions.InvalidDateRangeException;
import com.buildsmart.vendor.exception.CustomExceptions.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class ContractValidator {

    private final ProjectDateValidator projectDateValidator;

    public ContractValidator(ProjectDateValidator projectDateValidator) {
        this.projectDateValidator = projectDateValidator;
    }

    public void validate(ContractRequest dto, String vendorId) {
        Map<String, String> errors = new HashMap<>();

        if (vendorId == null || vendorId.isBlank()) {
            errors.put("vendorId", "Vendor ID is required");
        }

        if (dto.getProjectId() == null || dto.getProjectId().isBlank()) {
            errors.put("projectId", "Project ID is required");
        }

        if (dto.getStartDate() == null) {
            errors.put("startDate", "Start date is required");
        }

        if (dto.getEndDate() == null) {
            errors.put("endDate", "End date is required");
        }

        if (dto.getValue() == null) {
            errors.put("value", "Contract value is required");
        } else if (dto.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("value", "Contract value must be greater than zero");
        }

        if (dto.getStatus() == null) {
            errors.put("status", "Contract status is required");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // ID-format enforcement (Item 7): each ID must match its own pattern,
        // never another entity's. Run AFTER required-checks so the user gets
        // the right error first.
        IdFormatValidator.requireValid(IdFormatValidator.Kind.VENDOR, vendorId);
        IdFormatValidator.requireValid(IdFormatValidator.Kind.PROJECT, dto.getProjectId());
        IdFormatValidator.requireValid(IdFormatValidator.Kind.TASK, dto.getTaskId());

        // Cross-field validation
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && !dto.getStartDate().isBefore(dto.getEndDate())) {
            throw new InvalidDateRangeException("Start date must be before end date");
        }

        // Project-window enforcement (Item #3): contract must live entirely
        // inside its parent project's start/end window.
        projectDateValidator.validateRangeWithinProject(
                dto.getProjectId(), dto.getStartDate(), dto.getEndDate());
    }
}
