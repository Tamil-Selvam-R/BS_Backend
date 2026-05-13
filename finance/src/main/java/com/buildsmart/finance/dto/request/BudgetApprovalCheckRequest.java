package com.buildsmart.finance.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Synchronous budget availability check request used by Resource Allocation
 * service before creating an allocation.
 *
 * Mirrors the BudgetApprovalRequestDTO sent over the wire by the
 * resource-allocation FinanceServiceClient.
 *
 * Added for FEATURE SET 1 — to fix the endpoint mismatch where Resource
 * Allocation calls POST /api/budget/approve.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) keeps the DTO forward-compatible
 * if Resource Allocation adds new fields to its request payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetApprovalCheckRequest {

    private String allocationId;
    private String projectId;
    private String resourceId;
    private String resourceType;
    private String skillLevel;
    private String equipmentName;
    private String equipmentLevel;
    private Integer numberOfLabors;
    private Double costPerHour;
    private Integer totalHours;
    /** Total cost for which budget approval is being checked. */
    private Double totalCost;
    private String description;
}
