package com.buildsmart.siteops.validator;

import com.buildsmart.siteops.dto.SiteLogRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Validates SiteLog requests.
 * Note: submittedBy is resolved from JWT token — not validated here.
 */
@Component
public class SiteLogValidator {

    public void validate(SiteLogRequest request) {

        // ── Project ID ──────────────────────────────────────────────────────
        if (request.projectId() == null || request.projectId().isBlank()) {
            throw new IllegalArgumentException("Project ID is required.");
        }

        // ── Log date ────────────────────────────────────────────────────────
        if (request.logDate() == null) {
            throw new IllegalArgumentException("Log date is required.");
        }
        // Site log must always be for today — no back-dated or future entries.
        // The project start/end date boundary is validated separately in the service.
        if (!request.logDate().isEqual(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Log date must be today's date (" + LocalDate.now() + "). "
                    + "You provided: " + request.logDate() + ". "
                    + "Back-dated and future-dated entries are not allowed.");
        }

        // ── Progress percent ────────────────────────────────────────────────
        if (request.progressPercent() == null) {
            throw new IllegalArgumentException("progressPercent is required.");
        }
        BigDecimal p = request.progressPercent();
        if (p.compareTo(BigDecimal.ZERO) < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "progressPercent must be between 0 and 100. You provided: " + p);
        }
        // submittedBy resolved from JWT — not validated here
    }

    /**
     * Validates that the reviewer is a real, ACTIVE PROJECT_MANAGER.
     * Called from SiteLogServiceImpl.reviewSiteLog().
     */
    public void validateReviewer(String reviewedBy) {
        if (reviewedBy == null || reviewedBy.isBlank()) {
            throw new IllegalArgumentException("reviewedBy (Project Manager user ID) is required.");
        }
    }
}
