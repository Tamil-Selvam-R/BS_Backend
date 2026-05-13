package com.buildsmart.finance.controller;

import com.buildsmart.finance.dto.response.AssignedTaskResponse;
import com.buildsmart.finance.dto.response.AssignedTaskSyncResult;
import com.buildsmart.finance.entity.enums.AssignedTaskStatus;
import com.buildsmart.finance.service.AssignedTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Assigned Tasks", description = "Finance task management — sync from PM, list my tasks, submit for approval and handle PM decisions")
@RestController
@RequestMapping("/api/finance/tasks")
@RequiredArgsConstructor
public class AssignedTaskController {

    private final AssignedTaskService assignedTaskService;

    @Operation(summary = "Sync tasks from PM service", description = "Pulls tasks assigned to the current Finance Officer from the Project Manager service and upserts them locally")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sync completed — returns created/updated counts"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid Authorization header")
    })
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
    public ResponseEntity<AssignedTaskSyncResult> sync(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(assignedTaskService.syncTasksFromPm(authorization));
    }

    @Operation(summary = "List my assigned tasks", description = "Returns all tasks assigned to the authenticated Finance Officer, optionally filtered by status")
    @ApiResponse(responseCode = "200", description = "Task list returned")
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasks(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "Filter by status (IN_PROGRESS, SUBMITTED, APPROVED, REJECTED)", example = "IN_PROGRESS")
            @RequestParam(required = false) AssignedTaskStatus status) {
        if (status != null) {
            return ResponseEntity.ok(assignedTaskService.getMyTasksByStatus(authorization, status));
        }
        return ResponseEntity.ok(assignedTaskService.getMyTasks(authorization));
    }

    @Operation(summary = "List my tasks for a project", description = "Returns tasks assigned to the authenticated Finance Officer filtered by project")
    @ApiResponse(responseCode = "200", description = "Task list returned")
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasksForProject(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "Project ID") @PathVariable String projectId) {
        return ResponseEntity.ok(assignedTaskService.getMyTasksForProject(authorization, projectId));
    }

    @Operation(
        summary = "Submit a task for PM approval",
        description = "Moves the task from IN_PROGRESS to SUBMITTED and notifies the Project Manager. " +
                      "Body must contain a non-blank 'description' explaining the work done.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Missing or blank description in request body"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PostMapping("/{assignedTaskId}/submit")
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
    public ResponseEntity<AssignedTaskResponse> submitTask(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "Assigned task ID") @PathVariable String assignedTaskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Body must contain a 'description' field explaining the work done")
            @RequestBody java.util.Map<String, String> body) {
        if (body == null) {
            throw new IllegalArgumentException(
                    "Request body is required. Provide a 'description' explaining the work done.");
        }
        String description = body.getOrDefault("description", body.get("remarks"));
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "'description' is required and must explain the work done before submission.");
        }
        return ResponseEntity.ok(assignedTaskService.submitTask(authorization, assignedTaskId, description));
    }

    @Operation(
        summary = "Internal — PM approval callback",
        description = "Called by the PM service (via Feign) after approving or rejecting a submitted task. " +
                      "Body: { pmTaskId, decision: APPROVED|REJECTED, rejectionReason }")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Approval result recorded"),
        @ApiResponse(responseCode = "400", description = "Missing pmTaskId or decision")
    })
    @PatchMapping("/internal/approval-result")
    public ResponseEntity<AssignedTaskResponse> approvalResult(
            @RequestBody java.util.Map<String, String> payload) {
        String pmTaskId = payload.get("pmTaskId");
        String decision = payload.get("decision");
        String rejectionReason = payload.get("rejectionReason");
        if (pmTaskId == null || pmTaskId.isBlank() || decision == null || decision.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(assignedTaskService.handleApprovalResult(pmTaskId, decision, rejectionReason));
    }
}
