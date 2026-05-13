package com.buildsmart.finance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Service-to-service Feign client used by Finance to call resource-allocation
 * back after the Finance officer approves or rejects the budget that was
 * created from a Resource budget request.
 *
 * Endpoint hit:
 *   POST /api/internal/resources/{resourceId}/budget-result
 *
 * Body:
 *   { "decision": "APPROVED|REJECTED", "budgetId": "...", "rejectionReason": "..." }
 *
 * The endpoint is permitAll in resource-allocation (service-to-service only).
 */
@FeignClient(
        name = "resource-allocation",
        contextId = "financeResourceAllocationCallbackClient"
)
public interface ResourceAllocationCallbackClient {

    @PostMapping("/api/internal/resources/{resourceId}/budget-result")
    Map<String, Object> notifyBudgetResult(
            @PathVariable("resourceId") String resourceId,
            @RequestBody Map<String, String> payload);
}
