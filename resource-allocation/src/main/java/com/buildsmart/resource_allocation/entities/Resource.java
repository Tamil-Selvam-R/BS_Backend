package com.buildsmart.resource_allocation.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "resource")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Resource {

    @Id
    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "availability")
    private String availability;

    @Column(name = "number_of_labors")
    private Integer numberOfLabors;

    @Column(name = "skill_level")
    private String skillLevel;

    @Column(name = "equipment_name")
    private String equipmentName;

    @Column(name = "equipment_level")
    private String equipmentLevel;

    @Column(name = "cost_per_hour")
    private Double costPerHour;

    @Column(name = "total_cost")
    private Double totalCost;

    // ── Fields for the new Resource → Finance budget approval flow ────────────
    // None of these are required by the existing creation/listing logic; they are
    // populated only when a resource is created through the new flow.

    /** Project this resource belongs to. Used to drive Finance budget calculation. */
    @Column(name = "project_id", length = 30)
    private String projectId;

    /** Total hours requested for this resource. Drives totalCost = costPerHour * totalHours. */
    @Column(name = "total_hours")
    private Integer totalHours;

    /** Optional — issue this resource is intended to resolve. Echoed in the site notification. */
    @Column(name = "issue_id", length = 30)
    private String issueId;

    /** Optional — site this resource is intended for. Echoed in the site notification. */
    @Column(name = "site_id", length = 30)
    private String siteId;

    /** Site Engineer userId to notify once the allocation is auto-created. */
    @Column(name = "site_engineer_user_id", length = 50)
    private String siteEngineerUserId;

    /** Free text describing why this resource is needed. Sent to Finance for context. */
    @Column(name = "purpose", length = 1000)
    private String purpose;

}

