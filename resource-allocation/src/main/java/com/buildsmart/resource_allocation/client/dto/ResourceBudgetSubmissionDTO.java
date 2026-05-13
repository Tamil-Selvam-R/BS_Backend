package com.buildsmart.resource_allocation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbound payload to Finance for the new
 *   Resource creation -> Finance budget approval -> Allocation
 * flow.
 *
 * Sent by resource-allocation when a Resource is created. Finance creates
 * a Budget (status = SUBMITTED) linked back to {@code resourceId} so it can
 * call resource-allocation back when the Finance officer approves or rejects.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceBudgetSubmissionDTO {

    private String resourceId;
    private String projectId;
    private String resourceType;        // "Labor" or "Equipment"
    private String skillLevel;
    private String equipmentName;
    private String equipmentLevel;
    private Integer numberOfLabors;
    private Double costPerHour;
    private Integer totalHours;
    private Double totalCost;
    private String requestedBy;         // PM userId who created the resource
    private String purpose;             // free text for the finance officer
}
