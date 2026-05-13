package com.buildsmart.safety.service;

import com.buildsmart.safety.domain.model.AssignedTaskStatus;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.AssignedTaskResponse;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.SyncResult;

import java.util.List;

public interface AssignedTaskService {

    /**
     * Pulls TASK_ASSIGNED notifications from the project-service for the
     * currently authenticated officer, stores any new ones as AssignedTask
     * records, and creates popup SafetyNotifications for each new task.
     */
    SyncResult syncTasksFromPm();

    /** All tasks assigned to the current officer. */
    List<AssignedTaskResponse> getMyTasks();

    /** Tasks for the current officer filtered by status. */
    List<AssignedTaskResponse> getMyTasksByStatus(AssignedTaskStatus status);

    /** All tasks for a given project (current officer only). */
    List<AssignedTaskResponse> getMyTasksForProject(String projectId);

    /**
     * FEATURE SET 5 — Safety exception.
     * Submits a safety task. Safety tasks DO NOT go through the standard
     * approval cycle: once submitted they are auto-marked COMPLETED locally,
     * the PM-side task is patched to COMPLETED via Feign, and a notification
     * is fired to the dedicated notification service.
     *
     * @param assignedTaskId the local AssignedTask id (e.g. SAT001)
     * @param remarks        optional submission note
     * @return the updated task
     */
    AssignedTaskResponse submitTask(String assignedTaskId, String remarks);

    /**
     * Handle the approval-result callback fired by PM after the assigned task
     * has been approved or rejected. APPROVED → COMPLETED; REJECTED → REJECTED + reason.
     */
    AssignedTaskResponse handleApprovalResult(String pmTaskId, String decision, String rejectionReason);
}
