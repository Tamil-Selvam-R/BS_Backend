package com.buildsmart.vendor.validator;

import com.buildsmart.vendor.dto.request.DeliveryRequest;
import com.buildsmart.vendor.exception.CustomExceptions.ValidationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DeliveryValidator {

    public void validate(DeliveryRequest dto) {
        Map<String, String> errors = new HashMap<>();

        if (dto.getContractId() == null || dto.getContractId().isBlank()) {
            errors.put("contractId", "Contract ID is required");
        }

        if (dto.getDate() == null) {
            errors.put("date", "Delivery date is required");
        }

        if (dto.getItem() == null || dto.getItem().isBlank()) {
            errors.put("item", "Item is required");
        }

        if (dto.getQuantity() == null) {
            errors.put("quantity", "Quantity is required");
        } else if (dto.getQuantity() < 1) {
            errors.put("quantity", "Quantity must be at least 1");
        }

        if (dto.getStatus() == null) {
            errors.put("status", "Delivery status is required");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // ID-format enforcement (Item 7).
        IdFormatValidator.requireValid(IdFormatValidator.Kind.CONTRACT, dto.getContractId());
    }
}
