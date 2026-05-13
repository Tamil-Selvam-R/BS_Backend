package com.buildsmart.analytics.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SiteLogDTO(
        String logId,
        String projectId,
        LocalDate logDate,
        String activities,
        String issuesSummary,
        BigDecimal progressPercent,
        String submittedBy,
        LocalDateTime submittedAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewerComments,
        String photoUrl
) {
}

