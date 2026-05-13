package com.buildsmart.safety.client;

import com.buildsmart.safety.client.dto.ProjectDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;


@FeignClient(name = "project-service", contextId = "projectClient", fallback = ProjectClientFallback.class)
public interface ProjectClient {

    @GetMapping("/api/projects/{projectId}")
    ProjectDto getProject(@PathVariable String projectId,
                          @RequestHeader("Authorization") String bearerToken);

    /**
     * FEATURE SET 5 — Safety exception.
     * Used by Safety service to flip a PM-side task to COMPLETED immediately
     * after the safety officer submits work. Safety tasks skip the approval
     * cycle entirely, so we patch the status directly.
     */
    @PatchMapping("/api/projects/tasks/{taskId}/status")
    void updateTaskStatus(@PathVariable("taskId") String taskId,
                          @RequestParam("status") String status,
                          @RequestHeader("Authorization") String bearerToken);

    /**
     * Submit a PM task for approval via the service-to-service internal endpoint.
     * Safety officer's submission goes through PM's approval flow now (no auto-complete).
     * Body: { "description": "submission remarks" }
     *
     * NOTE: PM's context path is "/api", so the full URL is /api/internal/tasks/{taskId}/submit.
     */
    @PostMapping("/api/internal/tasks/{taskId}/submit")
    void submitTaskForApproval(@PathVariable("taskId") String taskId,
                               @RequestBody Map<String, String> body,
                               @RequestHeader("Authorization") String bearerToken);
}
