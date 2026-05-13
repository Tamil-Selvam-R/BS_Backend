package com.buildsmart.finance.controller;

import com.buildsmart.finance.dto.request.ResourceBudgetSubmissionRequest;
import com.buildsmart.finance.dto.response.ResourceBudgetSubmissionResponse;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.entity.enums.BudgetCategory;
import com.buildsmart.finance.entity.enums.BudgetStatus;
import com.buildsmart.finance.repository.BudgetRepository;
import com.buildsmart.finance.util.IdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Service-to-service endpoint that receives a budget request originating from
 * the resource-allocation service when a PM creates a Resource.
 *
 * Path: POST /api/finance/budget/resource-request
 */
@Tag(name = "Resource Budget Request", description = "Service-to-service endpoint for resource-allocation to submit budget requests to Finance")
@RestController
@RequestMapping("/api/finance/budget")
@RequiredArgsConstructor
@Slf4j
public class ResourceBudgetRequestController {

    private final BudgetRepository budgetRepository;

    @Operation(
        summary = "Submit a resource-driven budget request",
        description = "Called by the resource-allocation service when a PM creates a Resource. " +
                      "Creates a Budget row in SUBMITTED status linked to the originating resourceId via referenceResourceId. " +
                      "Finance officer reviews via /api/budgets/* endpoints.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget request accepted and created"),
        @ApiResponse(responseCode = "400", description = "Missing resourceId or projectId")
    })
    @PostMapping("/resource-request")
    public ResponseEntity<ResourceBudgetSubmissionResponse> submitResourceBudgetRequest(
            @RequestBody ResourceBudgetSubmissionRequest request) {

        if (request == null
                || request.getResourceId() == null || request.getResourceId().isBlank()
                || request.getProjectId() == null || request.getProjectId().isBlank()) {
            return ResponseEntity.badRequest().body(ResourceBudgetSubmissionResponse.builder()
                    .accepted(false)
                    .message("resourceId and projectId are required.")
                    .build());
        }

        BigDecimal planned = request.getTotalCost() != null
                ? BigDecimal.valueOf(request.getTotalCost())
                : BigDecimal.ZERO;

        BudgetCategory category = mapResourceTypeToCategory(request.getResourceType());

        Budget budget = Budget.builder()
                .budgetId(IdGenerator.generateBudgetId())
                .projectId(request.getProjectId())
                .budgetCategory(category)
                .plannedAmount(planned)
                .status(BudgetStatus.SUBMITTED)
                .createdBy(request.getRequestedBy() != null ? request.getRequestedBy() : "resource-allocation")
                .referenceResourceId(request.getResourceId())
                .isDeleted(false)
                .build();

        Budget saved = budgetRepository.save(budget);
        log.info("Resource-driven Budget {} created (resourceId={}, projectId={}, category={}, amount={})",
                saved.getBudgetId(), request.getResourceId(), request.getProjectId(), category, planned);

        return ResponseEntity.ok(ResourceBudgetSubmissionResponse.builder()
                .accepted(true)
                .budgetId(saved.getBudgetId())
                .resourceId(request.getResourceId())
                .message("Budget request created in SUBMITTED status.")
                .build());
    }

    private BudgetCategory mapResourceTypeToCategory(String resourceType) {
        if (resourceType == null) return BudgetCategory.MISCELLANEOUS;
        String upper = resourceType.trim().toUpperCase();
        return switch (upper) {
            case "LABOR"     -> BudgetCategory.LABOR;
            case "EQUIPMENT" -> BudgetCategory.EQUIPMENT;
            case "MATERIAL"  -> BudgetCategory.MATERIAL;
            default          -> BudgetCategory.MISCELLANEOUS;
        };
    }
}
