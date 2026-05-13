package com.buildsmart.resource_allocation.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllocationResponseDTO {

    private String allocationId;
    private String projectId;
    private String resourceId;
    private String resourceType;
    private String skillLevel;
    private String equipmentName;
    private String equipmentLevel;
    private Integer numberOfLabors;
    private Double costPerHour;
    private Double totalCost;
    private LocalDate assignedDate;
    private LocalDate releasedDate;
    private String status;
}

