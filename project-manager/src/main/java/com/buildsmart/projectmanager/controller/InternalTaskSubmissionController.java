package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.dto.TaskResponse;
import com.buildsmart.projectmanager.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal service-to-service endpoint used by downstream services
 * (Finance, Safety, SiteOps, Vendor) to submit a PM task for approval.
 *
 * When a downstream officer submits their work, they call their own service's
 * submit endpoint which in turn calls this endpoint via Feign
 * (e.g. PmTaskSubmissionClient in Finance calls POST /api/internal/tasks/{taskId}/submit).
 *
 * This controller:
 *  1. Sets the PM task status to AWAITING_APPROVAL
 *  2. Auto-creates an ApprovalRequest visible at GET /api/approvals/pending
 *
 * Security: open for service-to-service use (permitAll in SecurityConfig for /internal/**)
 */
@RestController
@RequestMapping("/internal/tasks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Task Submission", description = "Service-to-service endpoint for submitting tasks for PM approval")
public class InternalTaskSubmissionController {

    private final ProjectService projectService;

    /**
     * Downstream service calls this after the officer submits their local task.
     * Body: { "description": "work summary" }
     * Sets task → AWAITING_APPROVAL and creates ApprovalRequest in PM.
     */
    @PostMapping("/{taskId}/submit")
    @Operation(
            summary = "Submit a PM task for approval (service-to-service)",
            description = "Called by downstream services (Finance/Safety/SiteOps/Vendor) via Feign. "
                    + "Sets the task to AWAITING_APPROVAL and auto-creates an ApprovalRequest."
    )
    public ResponseEntity<TaskResponse> submitTaskForApproval(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, String> body) {

        String description = (body != null)
                ? body.getOrDefault("description", body.getOrDefault("remarks", ""))
                : "";

        log.info("[Internal] Task '{}' submitted for PM approval. Note: '{}'", taskId, description);
        TaskResponse response = projectService.submitTask(taskId, description);
        return ResponseEntity.ok(response);
    }
}
