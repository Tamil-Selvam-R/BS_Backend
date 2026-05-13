package com.buildsmart.siteops.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Submits a PM task for approval via PM's internal service-to-service endpoint.
 * Used when the site engineer submits an assigned task — PM creates an
 * ApprovalRequest visible at GET /api/approvals.
 *
 * NOTE: PM's context path is "/api", so the full URL is /api/internal/tasks/{taskId}/submit.
 * Endpoint is permitAll in PM's SecurityConfig but we forward the JWT anyway so the
 * audit trail in PM logs the calling officer.
 */
@FeignClient(
        name = "project-service",
        contextId = "siteOpsPmTaskSubmissionClient"
)
public interface PmTaskSubmissionClient {

    @PostMapping("/api/internal/tasks/{taskId}/submit")
    void submitTaskForApproval(
            @PathVariable("taskId") String taskId,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String bearerToken);
}
