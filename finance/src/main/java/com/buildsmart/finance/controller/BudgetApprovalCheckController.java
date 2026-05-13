package com.buildsmart.finance.controller;

import com.buildsmart.finance.dto.request.BudgetApprovalCheckRequest;
import com.buildsmart.finance.dto.response.BudgetApprovalCheckResponse;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.repository.BudgetRepository;
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
import java.util.List;

/**
 * Synchronous budget availability check endpoint, consumed by the
 * Resource Allocation service before it creates an allocation.
 *
 * Path: POST /api/budget/approve
 */
@Tag(name = "Budget Approval Check", description = "Service-to-service synchronous budget availability check used by Resource Allocation before creating an allocation")
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
@Slf4j
public class BudgetApprovalCheckController {

    private final BudgetRepository budgetRepository;

    @Operation(
        summary = "Check budget availability for a resource allocation",
        description = "Read-only check: sums all APPROVED budgets for the project, subtracts actual spend, " +
                      "and returns approved=true if the requested totalCost fits within the remaining headroom. " +
                      "Does NOT mutate any budget records.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Check result returned — inspect 'approved' field"),
        @ApiResponse(responseCode = "400", description = "Missing required fields")
    })
    @PostMapping("/approve")
    public ResponseEntity<BudgetApprovalCheckResponse> checkBudgetApproval(
            @RequestBody BudgetApprovalCheckRequest request) {

        log.info("POST /api/budget/approve - allocationId={}, projectId={}, totalCost={}",
                request.getAllocationId(), request.getProjectId(), request.getTotalCost());

        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            return ResponseEntity.ok(BudgetApprovalCheckResponse.builder()
                    .approved(false)
                    .message("projectId is required for budget approval check.")
                    .allocationId(request.getAllocationId())
                    .build());
        }
        if (request.getTotalCost() == null || request.getTotalCost() <= 0.0) {
            return ResponseEntity.ok(BudgetApprovalCheckResponse.builder()
                    .approved(false)
                    .message("totalCost must be a positive number.")
                    .projectId(request.getProjectId())
                    .allocationId(request.getAllocationId())
                    .build());
        }

        List<Budget> approvedBudgets =
                budgetRepository.findApprovedBudgetsByProjectId(request.getProjectId());

        if (approvedBudgets.isEmpty()) {
            String reason = "No APPROVED budget exists for project " + request.getProjectId()
                    + ". Allocation cannot proceed until Finance has at least one approved budget.";
            log.warn(reason);
            return ResponseEntity.ok(BudgetApprovalCheckResponse.builder()
                    .approved(false)
                    .message(reason)
                    .projectId(request.getProjectId())
                    .allocationId(request.getAllocationId())
                    .build());
        }

        BigDecimal totalPlanned = BigDecimal.ZERO;
        BigDecimal totalSpent   = BigDecimal.ZERO;
        for (Budget b : approvedBudgets) {
            if (b.getPlannedAmount() != null) {
                totalPlanned = totalPlanned.add(b.getPlannedAmount());
            }
            if (b.getActualAmount() != null) {
                totalSpent = totalSpent.add(b.getActualAmount());
            }
        }
        BigDecimal available = totalPlanned.subtract(totalSpent);
        BigDecimal requested = BigDecimal.valueOf(request.getTotalCost());

        if (requested.compareTo(available) > 0) {
            String reason = String.format(
                    "Requested cost %s exceeds remaining approved budget %s for project %s.",
                    requested, available, request.getProjectId());
            log.warn(reason);
            return ResponseEntity.ok(BudgetApprovalCheckResponse.builder()
                    .approved(false)
                    .message(reason)
                    .projectId(request.getProjectId())
                    .allocationId(request.getAllocationId())
                    .build());
        }

        log.info("Budget approval check OK for project {}: requested={}, available={}",
                request.getProjectId(), requested, available);
        return ResponseEntity.ok(BudgetApprovalCheckResponse.builder()
                .approved(true)
                .message("Budget approval granted.")
                .projectId(request.getProjectId())
                .allocationId(request.getAllocationId())
                .approvedAmount(request.getTotalCost())
                .build());
    }
}
