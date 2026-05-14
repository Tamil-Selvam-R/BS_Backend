package com.buildsmart.vendor.service;

import com.buildsmart.vendor.dto.response.AssignedTaskResponse;
import com.buildsmart.vendor.dto.response.AssignedTaskSyncResult;
import com.buildsmart.vendor.enums.AssignedTaskStatus;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface AssignedTaskService {

    /**
     * Pulls TASK_ASSIGNED notifications from the project-service for the
     * currently authenticated vendor and stores any new ones as AssignedTask
     * records.
     */
    AssignedTaskSyncResult syncTasksFromPm(HttpServletRequest request);

    List<AssignedTaskResponse> getMyTasks(HttpServletRequest request);

    List<AssignedTaskResponse> getMyTasksByStatus(HttpServletRequest request, AssignedTaskStatus status);

    List<AssignedTaskResponse> getMyTasksForProject(HttpServletRequest request, String projectId);

    /** Submit a vendor task to PM for approval. Local status moves to SUBMITTED. */
    AssignedTaskResponse submitTask(HttpServletRequest request, String assignedTaskId, String remarks);

    /** Handle PM's approve/reject callback. APPROVED → COMPLETED, REJECTED → REJECTED + reason. */
    AssignedTaskResponse handleApprovalResult(String pmTaskId, String decision, String rejectionReason);

    /** Returns all assigned tasks across all vendors — used by analytics service (no vendor filter). */
    List<AssignedTaskResponse> getAllTasks();

    /** Returns all assigned tasks with the given status — used by analytics service. */
    List<AssignedTaskResponse> getAllTasksByStatus(AssignedTaskStatus status);
}
