package com.buildsmart.analytics.client;

import java.time.LocalDate;

public record ResourceAllocationDTO(
        String allocationId,
        String projectId,
        ResourceItemDTO resource,
        LocalDate assignedDate,
        LocalDate releasedDate,
        String status
) {}

