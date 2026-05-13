package com.buildsmart.siteops.entity;

import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.IssueStatus;
import com.buildsmart.siteops.enums.ResourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a problem or risk observed at a construction site.
 * Issues can be standalone or linked to a SiteLog entry.
 * CRITICAL issues auto-trigger a notification to the Project Manager.
 */
@Getter
@Setter
@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @Column(name = "issue_id", nullable = false, updatable = false, length = 20)
    private String issueId;

    @Column(name = "project_id", nullable = false, length = 20)
    private String projectId;

    /**
     * Optional link to the SiteLog where this issue was first noted.
     */
    @Column(name = "log_id", length = 20)
    private String logId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 15)
    private IssueSeverity severity;

    /**
     * IAM userId of the person who reported this issue.
     */
    @Column(name = "reported_by", nullable = false, length = 20)
    private String reportedBy;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private IssueStatus status;

    /**
     * IAM userId of the person assigned to resolve this issue.
     */
    @Column(name = "assigned_to", length = 20)
    private String assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Approval ID generated when this issue is submitted.
     * The PM uses this ID (via /approvals/{approvalId}/approve-notify or reject-notify)
     * to approve or reject the issue and notify the Site Engineer.
     * Format: APRSE001, APRSE002, ...
     */
    @Column(name = "approval_id", length = 20, unique = true)
    private String approvalId;

    // ── Optional inline resource request ─────────────────────────────────────
    /**
     * If the SE needs resources to resolve this issue they can specify the type
     * (LABOR or EQUIPMENT) along with the period they are needed.
     * All three fields must be provided together, or all left null.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 20)
    private ResourceType resourceType;

    /**
     * Meaningful description of what is needed.
     * e.g. "Crane for moving ceiling slabs" or "6 labourers for slab flooring work".
     * Required when resourceType is provided.
     */
    @Column(name = "resource_description", columnDefinition = "TEXT")
    private String resourceDescription;

    @Column(name = "resource_from_date")
    private LocalDate resourceFromDate;

    @Column(name = "resource_to_date")
    private LocalDate resourceToDate;

    // ── PM Resolution fields ──────────────────────────────────────────────────
    /**
     * Resource Allocation ID provided by the PM when resolving the issue.
     * Set when the PM allocates a resource to resolve this issue.
     */
    @Column(name = "allocation_id", length = 30)
    private String allocationId;

    /**
     * Resource ID (from Resource Allocation service) allocated to resolve this issue.
     */
    @Column(name = "resource_id", length = 30)
    private String resourceId;
}
