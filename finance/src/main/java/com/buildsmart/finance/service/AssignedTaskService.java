package com.buildsmart.finance.service;

import com.buildsmart.finance.dto.response.AssignedTaskResponse;
import com.buildsmart.finance.dto.response.AssignedTaskSyncResult;
import com.buildsmart.finance.entity.enums.AssignedTaskStatus;

import java.util.List;

public interface AssignedTaskService {

    /**
     * Pulls TASK_ASSIGNED notifications from the project-service for the
     * currently authenticated finance officer and stores any new ones as
     * AssignedTask records.
     */
    AssignedTaskSyncResult syncTasksFromPm(String authorizationHeader);

    List<AssignedTaskResponse> getMyTasks(String authorizationHeader);

    List<AssignedTaskResponse> getMyTasksByStatus(String authorizationHeader, AssignedTaskStatus status);

    List<AssignedTaskResponse> getMyTasksForProject(String authorizationHeader, String projectId);

    /** Submit a finance task to PM for approval. Local status moves to SUBMITTED. */
    AssignedTaskResponse submitTask(String authorizationHeader, String assignedTaskId, String remarks);

    /** Handle PM's approve/reject callback. APPROVED → COMPLETED, REJECTED → REJECTED + reason. */
    AssignedTaskResponse handleApprovalResult(String pmTaskId, String decision, String rejectionReason);
}
