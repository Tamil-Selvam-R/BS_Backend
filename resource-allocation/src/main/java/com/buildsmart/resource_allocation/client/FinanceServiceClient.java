package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.BudgetApprovalRequestDTO;
import com.buildsmart.resource_allocation.client.dto.BudgetApprovalResponseDTO;
import com.buildsmart.resource_allocation.client.dto.BudgetStatusResponseDTO;
import com.buildsmart.resource_allocation.client.dto.ResourceBudgetSubmissionDTO;
import com.buildsmart.resource_allocation.client.dto.ResourceBudgetSubmissionResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "finance-service", url = "${finance.service.url}", fallback = FinanceServiceClientFallback.class)
public interface FinanceServiceClient {

    /**
     * Synchronous budget availability check used by the legacy allocation flow.
     * Kept untouched for backwards compatibility — the new Resource → Budget
     * flow uses {@link #submitResourceBudgetRequest} instead.
     */
    @PostMapping("/api/budget/approve")
    BudgetApprovalResponseDTO checkBudgetApproval(
            @RequestBody BudgetApprovalRequestDTO request,
            @RequestHeader("Authorization") String authorization);

    /**
     * NEW FLOW: when PM creates a Resource, push a budget request to Finance.
     * Finance creates a Budget in SUBMITTED state with referenceResourceId set.
     * When Finance officer approves it, Finance calls resource-allocation back
     * via /api/internal/resources/{resourceId}/budget-result.
     */
    @PostMapping("/api/finance/budget/resource-request")
    ResourceBudgetSubmissionResponseDTO submitResourceBudgetRequest(
            @RequestBody ResourceBudgetSubmissionDTO request,
            @RequestHeader("Authorization") String authorization);

    /**
     * NEW FLOW (status pull): resource-allocation calls this just before
     * creating an allocation to confirm Finance has APPROVED the budget for
     * the (projectId, resourceId) pair. Replaces the unreliable
     * notification-based path. Service-to-service — endpoint is permitAll
     * on the Finance side.
     */
    @GetMapping("/api/finance/budget/status")
    BudgetStatusResponseDTO getBudgetStatusByResource(
            @RequestParam("projectId") String projectId,
            @RequestParam("resourceId") String resourceId);
}
