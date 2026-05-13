package com.buildsmart.resource_allocation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDTO {

    private String resourceId;
    private String type;
    private String availability;
    private Integer numberOfLabors;
    private String skillLevel;
    private String equipmentName;
    private String equipmentLevel;
    private Double costPerHour;
    private Double totalCost;
}

