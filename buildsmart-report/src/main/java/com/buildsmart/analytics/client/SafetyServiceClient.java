package com.buildsmart.analytics.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "safety-service", url = "${safety.service.url}", fallback = SafetyServiceFallback.class)
public interface SafetyServiceClient {

    @GetMapping("/api/safety/incidents")
    SafetyPageResponseDTO<IncidentDTO> getIncidents(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size
    );

    @GetMapping("/api/safety/inspections")
    SafetyPageResponseDTO<InspectionSummaryDTO> getInspections(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size
    );
}