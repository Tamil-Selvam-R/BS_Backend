package com.buildsmart.safety.web.mapper;

import com.buildsmart.safety.domain.model.SafetyInspection;
import com.buildsmart.safety.web.dto.InspectionDtos.InspectionResponse;

public class InspectionMapper {

    private InspectionMapper() {}

    public static InspectionResponse toResponse(SafetyInspection inspection) {
        return new InspectionResponse(
                inspection.getInspectionId(),
                inspection.getProjectId(),
                inspection.getDate(),
                inspection.getOfficerId(),
                inspection.getOfficerName(),
                inspection.getInspectionType(),
                inspection.getFindings(),
                inspection.getStatus(),
                inspection.getAssignedTaskId()
        );
    }
}
