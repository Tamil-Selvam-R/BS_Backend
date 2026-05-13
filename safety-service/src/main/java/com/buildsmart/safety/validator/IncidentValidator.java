package com.buildsmart.safety.validator;

import com.buildsmart.safety.web.dto.IncidentDtos.CreateIncidentRequest;
import org.springframework.stereotype.Component;

@Component
public class IncidentValidator {

    public void validate(CreateIncidentRequest request) {
        if (request.projectId() == null || request.projectId().isBlank())
            throw new IllegalArgumentException("projectId must not be blank");
        if (request.description() == null || request.description().isBlank())
            throw new IllegalArgumentException("description must not be blank");
        if (request.severity() == null)
            throw new IllegalArgumentException("severity must not be null");
    }
}
