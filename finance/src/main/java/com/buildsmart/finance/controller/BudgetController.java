package com.buildsmart.finance.controller;

import com.buildsmart.finance.dto.request.BudgetApprovalRequest;
import com.buildsmart.finance.dto.request.BudgetCreateRequest;
import com.buildsmart.finance.dto.request.BudgetUpdateRequest;
import com.buildsmart.finance.dto.response.BudgetResponse;
import com.buildsmart.finance.dto.response.BudgetUtilizationResponse;
import com.buildsmart.finance.dto.response.PagedResponse;
import com.buildsmart.finance.dto.response.ProjectBudgetUtilizationResponse;
import com.buildsmart.finance.service.BudgetService;
import com.buildsmart.finance.util.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Budgets", description = "Budget lifecycle management — create, review, approve and track utilization")
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
public class BudgetController {

    private final BudgetService budgetService;

    /**
     * Create a new budget
     * POST /api/budgets
     * Required role: ADMIN or FINANCE_OFFICER
     */
    @Operation(summary = "Create a new budget", description = "Creates a DRAFT budget for the given project and task. The task must be assigned to the authenticated Finance Officer.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Budget created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or task not assigned to current user"),
        @ApiResponse(responseCode = "404", description = "Task or project not found")
    })
    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody BudgetCreateRequest request) {
        log.info("POST /api/budgets - Creating budget for project: {}", request.getProjectId());
        BudgetResponse response = budgetService.createBudget(request, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get budget by ID
     * GET /api/budgets/{budgetId}
     * Required role: ADMIN or FINANCE_OFFICER
     */
    @Operation(summary = "Get budget by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget found"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @GetMapping("/{budgetId}")
    public ResponseEntity<BudgetResponse> getBudget(
            @Parameter(description = "Budget ID") @PathVariable String budgetId) {
        log.info("GET /api/budgets/{} - Fetching budget", budgetId);
        BudgetResponse response = budgetService.getBudgetById(budgetId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get budgets for a project with pagination
     * GET /api/budgets/projects/{projectId}?page=0&size=10&sortBy=createdAt&sortOrder=DESC
     */
    @Operation(summary = "List budgets for a project", description = "Returns paginated budgets linked to the specified project")
    @ApiResponse(responseCode = "200", description = "Paginated budget list")
    @GetMapping("/projects/{projectId}")
    public ResponseEntity<PagedResponse<BudgetResponse>> getBudgetsByProject(
            @Parameter(description = "Project ID") @PathVariable String projectId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/budgets/projects/{} - Fetching budgets", projectId);

        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        PagedResponse<BudgetResponse> response = budgetService.getBudgetsByProjectId(projectId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get budget revisions
     * GET /api/budgets/{budgetId}/revisions?page=0&size=10
     */
    // @GetMapping("/{budgetId}/revisions")
    // public ResponseEntity<PagedResponse<BudgetResponse>> getBudgetRevisions(
    //         @PathVariable String budgetId,
    //         @RequestParam(defaultValue = "0") Integer page,
    //         @RequestParam(defaultValue = "10") Integer size,
    //         @RequestParam(defaultValue = "createdAt") String sortBy,
    //         @RequestParam(defaultValue = "DESC") String sortOrder) {
    //     log.info("GET /api/budgets/{}/revisions - Fetching revisions", budgetId);

    //     Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
    //     PagedResponse<BudgetResponse> response = budgetService.getBudgetRevisions(budgetId, pageable);
    //     return ResponseEntity.ok(response);
    // }

    /**
     * Submit budget for approval
     * POST /api/budgets/{budgetId}/submit
     */
    @Operation(summary = "Submit budget for approval", description = "Transitions budget from DRAFT to SUBMITTED status for PM review")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Budget is not in DRAFT status"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @PostMapping("/{budgetId}/submit")
    public ResponseEntity<BudgetResponse> submitBudgetForApproval(
            @Parameter(description = "Budget ID") @PathVariable String budgetId) {
        log.info("POST /api/budgets/{}/submit - Submitting budget", budgetId);
        BudgetResponse response = budgetService.submitBudgetForApproval(budgetId);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve/Reject budget (called by PM Service via Feign with PROJECT_MANAGER JWT)
     * POST /api/budgets/{budgetId}/approval
     */
    @Operation(summary = "Approve or reject a budget", description = "Called by the PM Service (or ADMIN) to approve or reject a submitted budget. On approval, resource-allocation is notified via Feign.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Approval decision recorded"),
        @ApiResponse(responseCode = "400", description = "Budget is not in SUBMITTED status"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @PostMapping("/{budgetId}/approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER', 'PROJECT_MANAGER')")
    public ResponseEntity<BudgetResponse> approveBudget(
            @Parameter(description = "Budget ID") @PathVariable String budgetId,
            @Valid @RequestBody BudgetApprovalRequest request) {
        log.info("POST /api/budgets/{}/approval - Processing approval", budgetId);
        BudgetResponse response = budgetService.approveBudget(budgetId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Create budget revision (when rejected)
     * POST /api/budgets/{parentBudgetId}/revisions
     */
    // @PostMapping("/{parentBudgetId}/revisions")
    // public ResponseEntity<BudgetResponse> createBudgetRevision(
    //         @PathVariable String parentBudgetId,
    //         @Valid @RequestBody BudgetCreateRequest request) {
    //     log.info("POST /api/budgets/{}/revisions - Creating revision", parentBudgetId);
    //     BudgetResponse response = budgetService.createBudgetRevision(parentBudgetId, request);
    //     return ResponseEntity.status(HttpStatus.CREATED).body(response);
    // }

    /**
     * Get budgets by status with pagination
     * GET /api/budgets/status/{status}?page=0&size=10
     * Also called by PM (PROJECT_MANAGER role) to fetch SUBMITTED budgets for review.
     */
    @Operation(summary = "List budgets by status", description = "Returns paginated budgets filtered by status. PROJECT_MANAGER can call this to fetch SUBMITTED budgets for review.")
    @ApiResponse(responseCode = "200", description = "Paginated budget list")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER', 'PROJECT_MANAGER')")
    public ResponseEntity<PagedResponse<BudgetResponse>> getBudgetsByStatus(
            @Parameter(description = "Budget status", example = "SUBMITTED") @PathVariable String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/budgets/status/{} - Fetching budgets", status);

        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        PagedResponse<BudgetResponse> response = budgetService.getBudgetsByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get budgets by creator with pagination
     * GET /api/budgets/users/{createdBy}?page=0&size=10
     */
    @Operation(summary = "List budgets created by a user")
    @ApiResponse(responseCode = "200", description = "Paginated budget list")
    @GetMapping("/users/{createdBy}")
    public ResponseEntity<PagedResponse<BudgetResponse>> getBudgetsByCreatedBy(
            @Parameter(description = "User ID (email)") @PathVariable String createdBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/budgets/users/{} - Fetching budgets", createdBy);

        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        PagedResponse<BudgetResponse> response = budgetService.getBudgetsByCreatedBy(createdBy, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Update budget (PATCH)
     * PATCH /api/budgets/{budgetId}
     * Only allowed for DRAFT status budgets
     * Can only update plannedAmount and budgetCategory
     */
    @Operation(summary = "Update a DRAFT budget", description = "Partial update — only plannedAmount and budgetCategory can be changed while the budget is in DRAFT status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget updated"),
        @ApiResponse(responseCode = "400", description = "Budget is not in DRAFT status"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @PatchMapping("/{budgetId}")
    public ResponseEntity<BudgetResponse> updateBudget(
            @Parameter(description = "Budget ID") @PathVariable String budgetId,
            @Valid @RequestBody BudgetUpdateRequest request) {
        log.info("PATCH /api/budgets/{} - Updating budget", budgetId);
        BudgetResponse response = budgetService.updateBudget(budgetId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete budget
     * DELETE /api/budgets/{budgetId}
     * Only allowed for DRAFT status budgets
     * Approved and Rejected budgets cannot be deleted
     */
    @Operation(summary = "Delete a DRAFT budget", description = "Soft-deletes the budget. Only DRAFT budgets can be deleted; APPROVED and REJECTED ones cannot.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Budget deleted"),
        @ApiResponse(responseCode = "400", description = "Budget is not in DRAFT status"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> deleteBudget(
            @Parameter(description = "Budget ID") @PathVariable String budgetId) {
        log.info("DELETE /api/budgets/{} - Deleting budget", budgetId);
        budgetService.deleteBudget(budgetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get utilization breakdown for a single budget
     * GET /api/budgets/{budgetId}/utilization
     * Returns: plannedAmount, actualAmount, remainingAmount, utilizationPercentage, overBudget flag
     */
    @Operation(summary = "Get utilization for a single budget", description = "Returns plannedAmount, actualAmount, remainingAmount, utilizationPercentage and overBudget flag for the given budget")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Utilization data returned"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @GetMapping("/{budgetId}/utilization")
    public ResponseEntity<BudgetUtilizationResponse> getBudgetUtilization(
            @Parameter(description = "Budget ID") @PathVariable String budgetId) {
        log.info("GET /api/budgets/{}/utilization - Fetching utilization", budgetId);
        return ResponseEntity.ok(budgetService.getBudgetUtilization(budgetId));
    }

    /**
     * Get utilization summary for all budgets in a project
     * GET /api/budgets/projects/{projectId}/utilization
     * Returns: total planned, total actual, overall utilization %, per-budget breakdown
     */
    @Operation(summary = "Get utilization summary for a project", description = "Returns total planned, total actual, overall utilization % and per-budget breakdown for all budgets in the project")
    @ApiResponse(responseCode = "200", description = "Project utilization summary returned")
    @GetMapping("/projects/{projectId}/utilization")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER', 'PROJECT_MANAGER')")
    public ResponseEntity<ProjectBudgetUtilizationResponse> getProjectBudgetUtilization(
            @Parameter(description = "Project ID") @PathVariable String projectId) {
        log.info("GET /api/budgets/projects/{}/utilization - Fetching project utilization", projectId);
        return ResponseEntity.ok(budgetService.getProjectBudgetUtilization(projectId));
    }
}
