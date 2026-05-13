package com.buildsmart.safety.web.mapper;

import com.buildsmart.safety.domain.model.AssignedTask;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.AssignedTaskResponse;

public class AssignedTaskMapper {

    private AssignedTaskMapper() {}

    public static AssignedTaskResponse toResponse(AssignedTask task) {
        return new AssignedTaskResponse(
                task.getId(),
                task.getPmTaskId(),
                task.getPmNotificationId(),
                task.getProjectId(),
                task.getAssignedTo(),
                task.getAssignedBy(),
                task.getDescription(),
                task.getStatus(),
                task.getLinkedInspectionId(),
                task.getSyncedAt(),
                task.getCompletedAt(),
                task.getRejectionReason()
        );
    }
}
