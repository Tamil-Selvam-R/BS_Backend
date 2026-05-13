package com.buildsmart.siteops.client.dto;

import java.time.LocalDate;

public record AllocationDto(
        String allocationId,
        String projectId,
        ResourceRef resource,
        LocalDate assignedDate,
        LocalDate releasedDate,
        String status
) {
    public record ResourceRef(
            String resourceId,
            String type,
            String availability,
            Integer numberOfLabors,
            String skillLevel,
            String equipmentName,
            String equipmentLevel,
            Double costPerHour,
            Double totalCost
    ) {}
}
