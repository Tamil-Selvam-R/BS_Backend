package com.buildsmart.analytics.client;

public record ResourceItemDTO(
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

