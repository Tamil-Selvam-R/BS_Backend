package com.buildsmart.finance.controller;

import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.repository.BudgetRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service-to-service status query endpoint for the
 *   Resource → Finance budget → Allocation
 * flow. Resource-allocation calls this before creating an allocation
 * to confirm Finance has approved the budget for a given (projectId, resourceId) pair.
 *
 * Path: GET /api/finance/budget/status?projectId=…&resourceId=…
 */
@Tag(name = "Resource Budget Status", description = "Service-to-service status query — resource-allocation polls this to confirm Finance has approved a resource budget before proceeding")
@RestController
@RequestMapping("/api/finance/budget")
@RequiredArgsConstructor
@Slf4j
public class ResourceBudgetStatusController {

    private final BudgetRepository budgetRepository;

    @Operation(
        summary = "Get budget approval status for a (projectId, resourceId) pair",
        description = "Returns the latest budget row for the pair and whether it is APPROVED. " +
                      "Returns found=false if no budget request exists yet. " +
                      "Response also includes rejectionReason when the budget was REJECTED.")
    @ApiResponse(responseCode = "200", description = "Status object returned — inspect 'approved' and 'status' fields")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @Parameter(description = "Project ID", required = true, example = "PRJ-2024-001")
            @RequestParam("projectId") String projectId,
            @Parameter(description = "Resource ID", required = true, example = "RES-2024-001")
            @RequestParam("resourceId") String resourceId) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("resourceId", resourceId);

        if (projectId == null || projectId.isBlank()
                || resourceId == null || resourceId.isBlank()) {
            body.put("found", false);
            body.put("approved", false);
            body.put("status", null);
            body.put("message", "projectId and resourceId are required.");
            return ResponseEntity.ok(body);
        }

        List<Budget> rows =
                budgetRepository.findLatestByProjectIdAndResourceId(projectId, resourceId);
        if (rows == null || rows.isEmpty()) {
            body.put("found", false);
            body.put("approved", false);
            body.put("status", null);
            body.put("message", "No budget request found for this (projectId, resourceId) pair.");
            return ResponseEntity.ok(body);
        }

        Budget latest = rows.get(0);
        boolean approved = latest.getStatus() != null
                && "APPROVED".equalsIgnoreCase(latest.getStatus().name());

        body.put("found", true);
        body.put("budgetId", latest.getBudgetId());
        body.put("status", latest.getStatus() != null ? latest.getStatus().name() : null);
        body.put("approved", approved);
        if (latest.getRejectionReason() != null && !latest.getRejectionReason().isBlank()) {
            body.put("rejectionReason", latest.getRejectionReason());
        }
        log.info("Budget status for ({}, {}): {} (budgetId={})",
                projectId, resourceId, latest.getStatus(), latest.getBudgetId());
        return ResponseEntity.ok(body);
    }
}
