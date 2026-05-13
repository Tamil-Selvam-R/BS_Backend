package com.buildsmart.safety.web.controller;

import com.buildsmart.safety.domain.model.AssignedTaskStatus;
import com.buildsmart.safety.service.AssignedTaskService;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.AssignedTaskResponse;
import com.buildsmart.safety.web.dto.AssignedTaskDtos.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safety/tasks")
@RequiredArgsConstructor
@Tag(name = "Assigned Tasks", description = "Tasks assigned by Project Manager to Safety Officers")
public class AssignedTaskController {

    private final AssignedTaskService assignedTaskService;

    @PostMapping("/sync")
    @Operation(summary = "Pull new TASK_ASSIGNED notifications from project-service and store them locally. "
            + "Call this on portal load to show popup notifications. (SAFETY_OFFICER / ADMIN only)")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<SyncResult> sync() {
        return ResponseEntity.ok(assignedTaskService.syncTasksFromPm());
    }

    @GetMapping
    @Operation(summary = "Get all tasks assigned to the current officer")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasks(
            @RequestParam(required = false) AssignedTaskStatus status) {
        if (status != null) {
            return ResponseEntity.ok(assignedTaskService.getMyTasksByStatus(status));
        }
        return ResponseEntity.ok(assignedTaskService.getMyTasks());
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get tasks assigned to the current officer for a specific project")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<List<AssignedTaskResponse>> getMyTasksForProject(
            @PathVariable String projectId) {
        return ResponseEntity.ok(assignedTaskService.getMyTasksForProject(projectId));
    }

    /**
     * Submit a safety task to PM for approval.
     * The local task moves to SUBMITTED and PM appears to receive an entry in
     * GET /api/approvals. PM's approve/reject calls back via the internal
     * /approval-result endpoint to flip the local status to COMPLETED/REJECTED.
     */
    @PostMapping("/{assignedTaskId}/submit")
    @Operation(summary = "Submit a safety task to PM for approval",
               description = "Body must include a non-blank 'description' field describing the work done.")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<AssignedTaskResponse> submitTask(
            @PathVariable String assignedTaskId,
            @RequestBody java.util.Map<String, String> body) {
        // Description is mandatory: the safety officer must explain what they did
        // before PM can review the work in GET /api/approvals.
        if (body == null) {
            throw new IllegalArgumentException(
                    "Request body is required. Provide a 'description' explaining the work done.");
        }
        String description = body.getOrDefault("description", body.get("remarks"));
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "'description' is required and must explain the work done before submission.");
        }
        return ResponseEntity.ok(assignedTaskService.submitTask(assignedTaskId, description));
    }

    /**
     * Internal callback invoked by PM (via Feign) after approve/reject.
     * Body: { "pmTaskId": "TASK001", "decision": "APPROVED|REJECTED", "rejectionReason": "..." }
     *
     * Open in SecurityConfig (no JWT) — service-to-service only.
     */
    @PatchMapping("/internal/approval-result")
    @Operation(summary = "[Internal] PM → Safety: approve/reject result for a previously submitted task")
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
