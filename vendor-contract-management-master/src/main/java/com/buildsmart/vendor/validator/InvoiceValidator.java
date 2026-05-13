package com.buildsmart.vendor.validator;

import com.buildsmart.vendor.dto.request.InvoiceRequest;
import com.buildsmart.vendor.exception.CustomExceptions.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class InvoiceValidator {

    public void validate(InvoiceRequest dto) {
        Map<String, String> errors = new HashMap<>();

        if (dto.getContractId() == null || dto.getContractId().isBlank()) {
            errors.put("contractId", "Contract ID is required");
        }

        if (dto.getAmount() == null) {
            errors.put("amount", "Amount is required");
        } else if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("amount", "Amount must be greater than zero");
        }

        if (dto.getDate() == null) {
            errors.put("date", "Invoice date is required");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // ID-format enforcement (Item 7): contractId and (optional) taskId
        // must match their own patterns.
        IdFormatValidator.requireValid(IdFormatValidator.Kind.CONTRACT, dto.getContractId());
        IdFormatValidator.requireValid(IdFormatValidator.Kind.TASK, dto.getTaskId());
    }
}
