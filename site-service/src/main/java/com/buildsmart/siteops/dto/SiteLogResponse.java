package com.buildsmart.siteops.dto;

import com.buildsmart.siteops.enums.SiteLogReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SiteLogResponse(
        String logId,
        String projectId,
        LocalDate logDate,
        String activities,
        String issuesSummary,
        BigDecimal progressPercent,
        String submittedBy,
        LocalDateTime submittedAt,
        SiteLogReviewStatus reviewStatus,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewerComments,
        String photoUrl,
        /** Approval ID sent to PM so they can approve/reject this log. */
        String approvalId
) {}


