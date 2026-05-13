package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.validator.constraint.WordCount;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SiteLogRequest(

        @NotBlank(message = "Project ID is required")
        String projectId,

        @NotNull(message = "Log date is required")
        LocalDate logDate,

        @NotBlank(message = "Activities are required")
        @WordCount(min = 20, max = 50, message = "Activities must be between 20 and 50 words")
        String activities,

        @NotBlank(message = "Issues summary is required")
        @Size(max = 2000, message = "Issues summary must not exceed 2000 characters")
        String issuesSummary,

        @NotNull(message = "Progress percent is required")
        @DecimalMin(value = "0.00", message = "Progress percent must be >= 0")
        @DecimalMax(value = "100.00", message = "Progress percent must be <= 100")
        BigDecimal progressPercent

        // submittedBy removed — resolved automatically from JWT token
) {}
