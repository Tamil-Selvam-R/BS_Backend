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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Budget REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - FINANCE_OFFICER: sees only budgets they created (createdBy = userId from JWT via IAM).
 *   - ADMIN: unrestricted access to all budgets.
 *
 * Finance JWT filter resolves userId via IAM Feign call and sets it as the Security principal,
 * so {@code auth.getName()} reliably returns the caller's userId.
 */
@Tag(name = "Budgets", description = "Budget lifecycle management — create, review, approve and track utilization")
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
public class BudgetController {

    private final BudgetService budgetService;

    @Operation(summary = "Create a new budget",
               description = "Creates a DRAFT budget for the given project and task. " +
                       "The task must be assigned to the authenticated Finance Officer.")
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

    @Operation(summary = "Get budget by ID",
               description = "**FINANCE_OFFICER**: returns 403 if the budget was not created by the calling user.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget found"),
        @ApiResponse(responseCode = "403", description = "Finance Officer accessing another user's budget"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @GetMapping("/{budgetId}")
    public ResponseEntity<BudgetResponse> getBudget(
            @Parameter(description = "Budget ID") @PathVariable String budgetId) {
        log.info("GET /api/budgets/{} - Fetching budget", budgetId);
        BudgetResponse response = budgetService.getBudgetById(budgetId);
        if (isFinanceOfficer()) {
            assertOwnership(response.getCreatedBy(), "budget");
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List budgets for a project",
               description = "**FINANCE_OFFICER**: returns only budgets for this project that were created by the calling user.\n\n" +
                       "**ADMIN**: returns all budgets for this project.")
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
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(budgetService.getBudgetsByProjectIdAndCreatedBy(projectId, currentUserId, pageable));
        }
        return ResponseEntity.ok(budgetService.getBudgetsByProjectId(projectId, pageable));
    }

    @Operation(summary = "Submit budget for approval",
               description = "Transitions budget from DRAFT to SUBMITTED status for PM review")
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

    @Operation(summary = "Approve or reject a budget",
               description = "Called by the PM Service (or ADMIN) to approve or reject a submitted budget. " +
                       "On approval, resource-allocation is notified via Feign.")
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

    @Operation(summary = "List budgets by status",
               description = "**FINANCE_OFFICER**: returns only their own budgets with the given status.\n\n" +
                       "**ADMIN / PROJECT_MANAGER**: returns all budgets with the given status.")
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
        if (isFinanceOfficer()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(budgetService.getBudgetsByStatusAndCreatedBy(status, currentUserId, pageable));
        }
        return ResponseEntity.ok(budgetService.getBudgetsByStatus(status, pageable));
    }

    @Operation(summary = "List budgets created by a user",
               description = "**FINANCE_OFFICER**: returns 403 if the createdBy param does not match the caller's userId.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponse(responseCode = "200", description = "Paginated budget list")
    @GetMapping("/users/{createdBy}")
    public ResponseEntity<PagedResponse<BudgetResponse>> getBudgetsByCreatedBy(
            @Parameter(description = "User ID") @PathVariable String createdBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        log.info("GET /api/budgets/users/{} - Fetching budgets", createdBy);
        if (isFinanceOfficer()) {
            assertUserIdMatchesCaller(createdBy, "budgets");
        }
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(budgetService.getBudgetsByCreatedBy(createdBy, pageable));
    }

    @Operation(summary = "Update a DRAFT budget",
               description = "Partial update — only plannedAmount and budgetCategory can be changed while the budget is in DRAFT status")
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

    @Operation(summary = "Delete a DRAFT budget",
               description = "Soft-deletes the budget. Only DRAFT budgets can be deleted; APPROVED and REJECTED ones cannot.")
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

    @Operation(summary = "Get utilization for a single budget",
               description = "**FINANCE_OFFICER**: returns 403 if the budget was not created by the calling user.\n\n" +
                       "**ADMIN**: unrestricted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Utilization data returned"),
        @ApiResponse(responseCode = "403", description = "Finance Officer accessing another user's budget"),
        @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    @GetMapping("/{budgetId}/utilization")
    public ResponseEntity<BudgetUtilizationResponse> getBudgetUtilization(
            @Parameter(description = "Budget ID") @PathVariable String budgetId) {
        log.info("GET /api/budgets/{}/utilization - Fetching utilization", budgetId);
        if (isFinanceOfficer()) {
            BudgetResponse budget = budgetService.getBudgetById(budgetId);
            assertOwnership(budget.getCreatedBy(), "budget");
        }
        return ResponseEntity.ok(budgetService.getBudgetUtilization(budgetId));
    }

    @Operation(summary = "Get utilization summary for a project",
               description = "Returns total planned, total actual, overall utilization % and per-budget breakdown " +
                       "for all budgets in the project")
    @ApiResponse(responseCode = "200", description = "Project utilization summary returned")
    @GetMapping("/projects/{projectId}/utilization")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER', 'PROJECT_MANAGER')")
    public ResponseEntity<ProjectBudgetUtilizationResponse> getProjectBudgetUtilization(
            @Parameter(description = "Project ID") @PathVariable String projectId) {
        log.info("GET /api/budgets/projects/{}/utilization - Fetching project utilization", projectId);
        return ResponseEntity.ok(budgetService.getProjectBudgetUtilization(projectId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the current user's userId from the Security context (resolved by IAM Feign in the JWT filter). */
    private String resolveCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    /** True when the current JWT holder has the FINANCE_OFFICER role. */
    private boolean isFinanceOfficer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FINANCE_OFFICER"));
    }

    /**
     * Throws 403 if the resource's createdBy does not match the authenticated caller's userId.
     * Used to guard single-resource GET endpoints.
     */
    private void assertOwnership(String resourceCreatedBy, String resourceType) {
        String currentUserId = resolveCurrentUserId();
        if (!currentUserId.equals(resourceCreatedBy)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: this " + resourceType + " was not created by your account.");
        }
    }

    /**
     * Throws 403 if the given userId path param does not match the authenticated caller's userId.
     * Prevents a FINANCE_OFFICER from querying another user's records via the /users/{createdBy} path.
     */
    private void assertUserIdMatchesCaller(String userId, String resourceType) {
        String currentUserId = resolveCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you can only view your own " + resourceType + ".");
        }
    }
}
