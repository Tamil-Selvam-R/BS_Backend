package com.buildsmart.vendor.validator;

import com.buildsmart.vendor.dto.request.DocumentRequest;
import com.buildsmart.vendor.exception.CustomExceptions.ValidationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DocumentValidator {

    public void validate(DocumentRequest dto) {
        Map<String, String> errors = new HashMap<>();

        if (dto.getVendorId() == null || dto.getVendorId().isBlank()) {
            errors.put("vendorId", "Vendor ID is required");
        }

        if (dto.getDocumentName() == null || dto.getDocumentName().isBlank()) {
            errors.put("documentName", "Document name is required");
        }

        if (dto.getDocumentType() == null) {
            errors.put("documentType", "Document type is required");
        }

        if (dto.getFilePath() == null || dto.getFilePath().isBlank()) {
            errors.put("filePath", "File path is required");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // ID-format enforcement (Item 7): vendorId is mandatory and must be
        // BSVMxxx; the optional fields below must match their patterns when
        // supplied (DocumentRequest carries projectId, taskId, contractId).
        IdFormatValidator.requireValid(IdFormatValidator.Kind.VENDOR, dto.getVendorId());
        IdFormatValidator.requireValid(IdFormatValidator.Kind.PROJECT, dto.getProjectId());
        IdFormatValidator.requireValid(IdFormatValidator.Kind.TASK, dto.getTaskId());
        IdFormatValidator.requireValid(IdFormatValidator.Kind.CONTRACT, dto.getContractId());
    }
}
