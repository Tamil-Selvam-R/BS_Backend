package com.buildsmart.resource_allocation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetApprovalRequestDTO {

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
    private Double totalCost;
    private String description;
}

