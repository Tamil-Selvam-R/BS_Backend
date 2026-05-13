package com.buildsmart.siteops.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial-update request for a SiteLog.
 * Only activities, issuesSummary, and progressPercent may be changed.
 * projectId and logDate are identity fields and cannot be updated.
 */
public record SiteLogUpdateRequest(

        @Size(max = 2000, message = "Activities must not exceed 2000 characters")
        String activities,

        @Size(max = 2000, message = "Issues summary must not exceed 2000 characters")
        String issuesSummary,

        @DecimalMin(value = "0.00", message = "Progress percent must be >= 0")
        @DecimalMax(value = "100.00", message = "Progress percent must be <= 100")
        BigDecimal progressPercent
) {}
