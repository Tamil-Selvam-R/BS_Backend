package com.buildsmart.finance.client.dto;

import java.time.LocalDate;

public record AllocationDto(
        String allocationId,
        String projectId,
        String resourceId,
        String resourceType,
        Double totalCost,
        LocalDate assignedDate,
        LocalDate releasedDate
) {}
