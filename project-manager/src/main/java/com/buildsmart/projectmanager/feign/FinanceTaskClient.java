package com.buildsmart.projectmanager.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PM-side Feign client for the Finance service's Assigned Task APIs.
 *
 * When PM assigns a task to a FINANCE_OFFICER, PM calls
 * POST /api/finance/tasks/sync (fire-and-forget) so that Finance
 * immediately creates the assigned task record — no manual sync needed.
 *
 * The sync endpoint in Finance uses the Authorization header to identify
 * the current finance officer, then polls PM's
 *   GET /api/notifications/to/{userId}
 * for TASK_ASSIGNED notifications and stores them locally.
 *
 * DTOs are derived from Finance module's:
 *   - AssignedTaskResponse  (finance.dto.response.AssignedTaskResponse)
 *   - AssignedTaskSyncResult (finance.dto.response.AssignedTaskSyncResult)
 *
 * Eureka service name: finance-service (port 8085).
 * contextId avoids conflict with pmFinanceClient bean.
 */
@FeignClient(
        name = "finance-service",
        contextId = "pmFinanceTaskClient"
)
public interface FinanceTaskClient {

    /**
     * Triggers Finance to pull the latest TASK_ASSIGNED notifications from PM
     * for the user identified by the Authorization header.
     *
     * Called by PM (fire-and-forget) after creating a task for a FINANCE_OFFICER.
     * The Finance service uses the Authorization header to resolve the
     * finance officer's userId and calls PM's
     *   GET /api/notifications/to/{userId}
     * to discover new tasks.
     *
     * NOTE: Finance's endpoint requires FINANCE_OFFICER or ADMIN role.
     * PM passes the current request's JWT; the call may be a no-op
     * if PM's token does not satisfy Finance's role check.
     * The pull-based fallback (Finance officer manually syncing) always works.
     */
    @PostMapping("/api/finance/tasks/sync")
    AssignedTaskSyncResult triggerSync(@RequestHeader("Authorization") String authorization);

    /**
     * Get all Finance tasks for the Finance officer identified by the JWT.
     * PM can use this to verify a Finance officer received their task assignments.
     */
    @GetMapping("/api/finance/tasks")
    List<AssignedTaskResponse> getFinanceTasks(@RequestHeader("Authorization") String authorization);

    /**
     * Get Finance tasks for a specific project for the Finance officer in the JWT.
     */
    @GetMapping("/api/finance/tasks/project/{projectId}")
    List<AssignedTaskResponse> getFinanceTasksByProject(
            @PathVariable("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);

    // ── DTOs — mirrors Finance module's AssignedTaskResponse / AssignedTaskSyncResult ──

    /**
     * Mirrors finance.dto.response.AssignedTaskResponse.
     * Fields match exactly so Jackson can deserialize Finance's response.
     */
    record AssignedTaskResponse(
            String id,
            String pmTaskId,
            String pmNotificationId,
            String projectId,
            String assignedTo,
            String assignedBy,
            String description,
            String status,           // AssignedTaskStatus enum serialised as String
            String linkedEntityId,
            LocalDateTime syncedAt,
            LocalDateTime completedAt
    ) {}

    /**
     * Mirrors finance.dto.response.AssignedTaskSyncResult.
     */
    record AssignedTaskSyncResult(
            int newTasksSynced,
            int alreadyExisted,
            List<AssignedTaskResponse> newTasks
    ) {}
}
