package com.buildsmart.finance.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound payload for the new
 *   POST /api/finance/budget/resource-request
 * endpoint. Sent by the resource-allocation service when a PM creates a
 * Resource that needs Finance budget approval.
 *
 * Mirror of resource_allocation.client.dto.ResourceBudgetSubmissionDTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceBudgetSubmissionRequest {

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
    private String requestedBy;
    private String purpose;
}
