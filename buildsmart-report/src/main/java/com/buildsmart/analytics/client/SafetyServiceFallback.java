package com.buildsmart.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SafetyServiceFallback implements SafetyServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SafetyServiceFallback.class);

    @Override
    public SafetyPageResponseDTO<IncidentDTO> getIncidents(
            String projectId, String status, String severity,
            String dateFrom, String dateTo, int page, int size) {
        log.warn("[Fallback][SafetyService] getIncidents() — downstream unavailable, returning empty page");
        return new SafetyPageResponseDTO<>(List.of(), 0, size, 0, 0);
    }

    @Override
    public SafetyPageResponseDTO<InspectionSummaryDTO> getInspections(
            String projectId, String status, String dateFrom, String dateTo,
            int page, int size) {
        log.warn("[Fallback][SafetyService] getInspections() — downstream unavailable, returning empty page");
        return new SafetyPageResponseDTO<>(List.of(), 0, size, 0, 0);
    }
}
