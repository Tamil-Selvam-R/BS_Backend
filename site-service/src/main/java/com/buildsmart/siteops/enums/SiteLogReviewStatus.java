package com.buildsmart.siteops.enums;

/**
 * PM review status for a daily site log.
 *
 * PENDING   — Created by the SE; not yet sent to PM for approval.
 * SUBMITTED — Sent to PM for approval; awaiting decision.
 * APPROVED  — PM accepted the log as accurate.
 * REJECTED  — PM rejected the log with comments (SE may correct and resubmit).
 */
public enum SiteLogReviewStatus {
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED
}
