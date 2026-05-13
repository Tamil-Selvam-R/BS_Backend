package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.BudgetApprovalRequestDTO;
import com.buildsmart.resource_allocation.client.dto.BudgetApprovalResponseDTO;
import com.buildsmart.resource_allocation.client.dto.BudgetStatusResponseDTO;
import com.buildsmart.resource_allocation.client.dto.ResourceBudgetSubmissionDTO;
import com.buildsmart.resource_allocation.client.dto.ResourceBudgetSubmissionResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FinanceServiceClientFallback implements FinanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceServiceClientFallback.class);

    @Override
    public BudgetApprovalResponseDTO checkBudgetApproval(BudgetApprovalRequestDTO request, String authorization) {
        log.warn("[Fallback][FinanceService] checkBudgetApproval() - Finance service is unavailable. Allocation will be saved with Pending status.");
        BudgetApprovalResponseDTO fallbackResponse = new BudgetApprovalResponseDTO();
        fallbackResponse.setApproved(false);
        fallbackResponse.setMessage("Finance service is currently unavailable. Allocation is saved as Pending and will be synced once the Finance service is back.");
        fallbackResponse.setProjectId(request.getProjectId());
        fallbackResponse.setAllocationId(request.getAllocationId());
        fallbackResponse.setApprovedAmount(0.0);
        return fallbackResponse;
    }

    @Override
    public ResourceBudgetSubmissionResponseDTO submitResourceBudgetRequest(
            ResourceBudgetSubmissionDTO request, String authorization) {
        log.warn("[Fallback][FinanceService] submitResourceBudgetRequest() - Finance service unreachable for resourceId={}",
                request != null ? request.getResourceId() : "(null)");
        return ResourceBudgetSubmissionResponseDTO.builder()
                .accepted(false)
                .resourceId(request != null ? request.getResourceId() : null)
                .message("Finance service unavailable. Resource saved with PENDING_BUDGET; please retry budget request later.")
                .build();
    }

    @Override
    public BudgetStatusResponseDTO getBudgetStatusByResource(String projectId, String resourceId) {
        log.warn("[Fallback][FinanceService] getBudgetStatusByResource() - Finance service unreachable for ({}, {}).",
                projectId, resourceId);
        return BudgetStatusResponseDTO.builder()
                .projectId(projectId)
                .resourceId(resourceId)
                .found(false)
                .approved(false)
                .message("Finance service unavailable. Cannot verify budget approval; allocation must be retried later.")
                .build();
    }
}

