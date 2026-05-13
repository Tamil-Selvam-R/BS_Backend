package com.buildsmart.analytics.client;

import java.time.LocalDate;

public record InspectionSummaryDTO(
    String inspectionId,
    String projectId,
    LocalDate date,
    String officerId,
    String officerName,
    String inspectionType,
    String findings,
    String status
) {}
