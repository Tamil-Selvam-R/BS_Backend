package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.enums.SiteLogReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Used by a Project Manager to approve or reject a SiteLog.
 * reviewedBy is resolved from the JWT token — not sent in the body.
 */
public record SiteLogReviewRequest(

        @NotNull(message = "Review decision (APPROVED or REJECTED) is required")
        SiteLogReviewStatus status,

        @Size(max = 1000, message = "Reviewer comments must not exceed 1000 characters")
        String reviewerComments
) {}


