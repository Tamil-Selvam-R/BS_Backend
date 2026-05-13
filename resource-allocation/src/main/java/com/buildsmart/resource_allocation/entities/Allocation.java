package com.buildsmart.resource_allocation.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "allocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Allocation {

    @Id
    @Column(name = "allocation_id")
    private String allocationId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @ManyToOne
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "assigned_date")
    private LocalDate assignedDate;

    @Column(name = "released_date")
    private LocalDate releasedDate;

    @Column(name = "status")
    private String status;

    /**
     * Optional link to the Issue (from SiteOps) that triggered this allocation.
     * Required by FEATURE SET 1: allocation must be linked to the originating issue.
     * Nullable for backward compatibility with allocations created before this field existed.
     */
    @Column(name = "issue_id", length = 30)
    private String issueId;

    /**
     * Optional link to the Site where the resource is allocated.
     * Required by FEATURE SET 1: allocation must be linked to the site.
     */
    @Column(name = "site_id", length = 30)
    private String siteId;
}

