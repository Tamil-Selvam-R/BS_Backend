package com.buildsmart.siteops.service;

import com.buildsmart.siteops.dto.AssignedTaskResponse;
import com.buildsmart.siteops.dto.AssignedTaskSyncResult;
import com.buildsmart.siteops.enums.AssignedTaskStatus;

import java.util.List;

public interface AssignedTaskService {

    /**
     * Pulls TASK_ASSIGNED notifications from the project-service for the
     * currently authenticated site engineer and stores any new ones as
     * AssignedTask records.
     */
    AssignedTaskSyncResult syncTasksFromPm(String authorizationHeader);

    /** All tasks assigned to the current site engineer. */
    List<AssignedTaskResponse> getMyTasks(String authorizationHeader);

    /** Tasks for the current site engineer filtered by status. */
    List<AssignedTaskResponse> getMyTasksByStatus(String authorizationHeader, AssignedTaskStatus status);

    /** All tasks for a given project (current site engineer only). */
    List<AssignedTaskResponse> getMyTasksForProject(String authorizationHeader, String projectId);

    /**
     * Submits a site task to PM for approval. Local status moves to SUBMITTED.
     */
    AssignedTaskResponse submitTask(String authorizationHeader, String assignedTaskId, String remarks);

    /**
     * Handle PM's approve/reject callback. APPROVED → COMPLETED, REJECTED → REJECTED + reason.
     */
    AssignedTaskResponse handleApprovalResult(String pmTaskId, String decision, String rejectionReason);
}
