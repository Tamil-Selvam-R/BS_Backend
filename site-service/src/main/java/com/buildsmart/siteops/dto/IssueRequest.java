package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.ResourceType;
import com.buildsmart.siteops.validator.constraint.WordCount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record IssueRequest(

        @NotBlank(message = "Project ID is required")
        String projectId,

        /**
         * Required: links this issue to the SiteLog where it was first observed.
         * You can only report an issue from an existing site log.
         */
        @NotBlank(message = "Log ID is required — an issue must be linked to an existing site log")
        String logId,

        @NotBlank(message = "Description is required")
        @WordCount(min = 5, max = 20, message = "Description must be between 5 and 20 words")
        String description,

        @NotNull(message = "Severity is required (LOW, MEDIUM, HIGH, CRITICAL)")
        IssueSeverity severity,

        // ── Optional inline resource request ────────────────────────────────
        /** Leave null if no resources are needed. If provided, all fields below are required. */
        ResourceType resourceType,

        /**
         * Meaningful description of the resource needed.
         * e.g. "Crane for moving ceiling slabs" or "6 labourers for slab flooring work".
         */
        String resourceDescription,

        /** Start date of the resource requirement (inclusive). */
        LocalDate resourceFromDate,

        /** End date of the resource requirement (inclusive). */
        LocalDate resourceToDate

        // assignedTo is NOT accepted at creation time.
        // The Project Manager assigns someone later via PATCH /api/issues/{issueId}
) {}
