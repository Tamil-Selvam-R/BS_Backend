package com.buildsmart.siteops.entity;

import com.buildsmart.siteops.enums.SiteLogReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table(name = "site_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_site_log_project_date",
                columnNames = {"project_id", "log_date"}))
public class SiteLog {

    @Id
    @Column(name = "log_id", nullable = false, updatable = false, length = 20)
    private String logId;

    /**
     * FK to Project (String ID matching projects table).
     */
    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "activities", columnDefinition = "TEXT")
    private String activities;

    /**
     * Brief summary of issues noted in the log (free text; detailed issues tracked in Issue entity).
     */
    @Column(name = "issues_summary", columnDefinition = "TEXT")
    private String issuesSummary;

    /**
     * Cumulative project progress as of this log date (0.00–100.00).
     */
    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;

    /**
     * IAM userId of the site engineer who submitted this log.
     */
    @Column(name = "submitted_by", nullable = false, length = 20)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /**
     * PM review decision: PENDING (default), APPROVED, or REJECTED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 15)
    private SiteLogReviewStatus reviewStatus = SiteLogReviewStatus.PENDING;

    /**
     * IAM userId of the reviewer (PM or senior engineer). Nullable until reviewed.
     */
    @Column(name = "reviewed_by", length = 20)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_comments", columnDefinition = "TEXT")
    private String reviewerComments;

    /** File path / URL of the photo attached to this log. */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * Approval ID generated when this log is submitted (e.g. APRSE001).
     * PM uses this ID to approve or reject the log via the approval-result endpoint.
     */
    @Column(name = "approval_id", length = 20)
    private String approvalId;
}


