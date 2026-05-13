package com.buildsmart.vendor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Submits a PM task for approval via PM's internal service-to-service endpoint.
 * Used when a vendor submits an assigned task — PM creates an
 * ApprovalRequest visible at GET /api/approvals.
 */
@FeignClient(
        name = "project-service",
        contextId = "vendorPmTaskSubmissionClient"
)
public interface PmTaskSubmissionClient {

    @PostMapping("/api/internal/tasks/{taskId}/submit")
    void submitTaskForApproval(
            @PathVariable("taskId") String taskId,
            @RequestBody Map<String, String> body);
}
