package com.buildsmart.siteops.validator;

import com.buildsmart.siteops.dto.IssueRequest;
import org.springframework.stereotype.Component;

/**
 * Validates Issue requests.
 * Note: reportedBy is resolved from JWT token — not validated here.
 */
@Component
public class IssueValidator {

    public void validate(IssueRequest request) {

        if (request.projectId() == null || request.projectId().isBlank()) {
            throw new IllegalArgumentException("Project ID is required.");
        }

        if (request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("Issue description is required.");
        }

        if (request.severity() == null) {
            throw new IllegalArgumentException(
                    "Severity is required. Allowed values: LOW, MEDIUM, HIGH, CRITICAL.");
        }
        // reportedBy resolved from JWT — not validated here
    }

    public void validateUpdater(String userId, String fieldName) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(fieldName + " (user ID) is required.");
        }
    }
}
