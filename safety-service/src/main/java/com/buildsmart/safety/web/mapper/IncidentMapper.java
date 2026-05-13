package com.buildsmart.safety.web.mapper;

import com.buildsmart.safety.domain.model.Incident;
import com.buildsmart.safety.web.dto.IncidentDtos.IncidentResponse;

public class IncidentMapper {

    private IncidentMapper() {}

    public static IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getIncidentId(),
                incident.getProjectId(),
                incident.getDate(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getReportedBy(),
                incident.getReportedByName(),
                incident.getStatus()
        );
    }
}
