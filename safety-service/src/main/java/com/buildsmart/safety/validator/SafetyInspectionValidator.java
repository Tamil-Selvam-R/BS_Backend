package com.buildsmart.safety.validator;

import com.buildsmart.safety.web.dto.InspectionDtos.CreateInspectionRequest;
import org.springframework.stereotype.Component;

@Component
public class SafetyInspectionValidator {

    public void validate(CreateInspectionRequest request) {
        if (request.projectId() == null || request.projectId().isBlank())
            throw new IllegalArgumentException("projectId must not be blank");
        if (request.inspectionType() == null)
            throw new IllegalArgumentException("inspectionType must not be null");
        if (request.findings() == null || request.findings().isBlank())
            throw new IllegalArgumentException("findings must not be blank");
        if (request.findings().length() < 20)
            throw new IllegalArgumentException("findings must be at least 20 characters");
        if (request.findings().length() > 200)
            throw new IllegalArgumentException("findings must not exceed 200 characters");
    }
}
