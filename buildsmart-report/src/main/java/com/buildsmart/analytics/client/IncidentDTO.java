package com.buildsmart.analytics.client;

import java.time.LocalDate;

public record IncidentDTO(
    String incidentId,
    String projectId,
    LocalDate date,
    String description,
    String severity,
    String reportedBy,
    String reportedByName,
    String status
) {}
