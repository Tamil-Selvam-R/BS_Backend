package com.buildsmart.finance.service.impl;
import com.buildsmart.finance.client.NotificationServiceClient;
import com.buildsmart.finance.client.ProjectClient;
import com.buildsmart.finance.client.ResourceAllocationCallbackClient;
import com.buildsmart.finance.client.UserClient;
import com.buildsmart.finance.client.dto.ProjectDto;
import com.buildsmart.finance.dto.request.BudgetApprovalRequest;
import com.buildsmart.finance.dto.request.BudgetCreateRequest;
import com.buildsmart.finance.dto.request.BudgetUpdateRequest;
import com.buildsmart.finance.dto.response.BudgetResponse;
import com.buildsmart.finance.dto.response.BudgetUtilizationResponse;
import com.buildsmart.finance.dto.response.PagedResponse;
import com.buildsmart.finance.dto.response.ProjectBudgetUtilizationResponse;
import com.buildsmart.finance.entity.AssignedTask;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.entity.enums.BudgetStatus;
import com.buildsmart.finance.exception.ResourceNotFoundException;
import com.buildsmart.finance.repository.AssignedTaskRepository;
import com.buildsmart.finance.repository.BudgetRepository;
import com.buildsmart.finance.repository.PaymentRepository;
import com.buildsmart.finance.service.BudgetService;
import com.buildsmart.finance.util.IdGenerator;
import com.buildsmart.finance.validator.BudgetValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BudgetServiceImpl implements BudgetService {
    private final BudgetRepository budgetRepository;
    private final AssignedTaskRepository assignedTaskRepository;
    private final PaymentRepository paymentRepository;
    private final BudgetValidator budgetValidator;
    private final ProjectClient projectServiceClient;
    private final UserClient userClient;
    /** FEATURE SET 6 — push approve/reject events to dedicated notification service. */
    private final NotificationServiceClient notificationServiceClient;
    /**
     * NEW FLOW — used to call resource-allocation back when a Budget that was
     * created from a resource-allocation request is approved or rejected.
     * Optional: missing bean is tolerated so existing budget flows still work.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ResourceAllocationCallbackClient resourceAllocationCallbackClient;
    private static final String BEARER_PREFIX = "Bearer ";
    @Override
    public BudgetResponse createBudget(BudgetCreateRequest request, String authorizationHeader) {
        log.info("Creating budget for project: {}", request.getProjectId());
        // Read userId from SecurityContext — JwtAuthenticationFilter already called IAM
        // and stored the resolved userId (e.g. "BSFO001") as the principal before this
        // method runs. Direct JWT parsing is unreliable because the IAM and finance
        // services use different key-derivation strategies for the JWT secret.
        String createdBy = resolveCurrentUserId();
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalStateException("Cannot resolve current user. Please re-login.");
        }
        // Validate task ownership — the taskId must be assigned to the current user
        AssignedTask task = assignedTaskRepository.findByPmTaskId(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-004",
                        request.getTaskId(),
                        "Task not found with ID: " + request.getTaskId() +
                                ". Make sure you have synced your tasks via POST /api/finance/tasks/sync"
                ));
        if (!createdBy.equals(task.getAssignedTo())) {
            throw new IllegalArgumentException(
                    "Task " + request.getTaskId() + " is not assigned to you (your ID: " + createdBy + "). " +
                            "You can only create a budget against your own assigned tasks.");
        }
        // Validate budget creation (checks project exists, duplicate category, budget limit)
        budgetValidator.validateBudgetCreation(request);
        // Generate ID and save
        String budgetId = IdGenerator.generateBudgetId();
        Budget budget = Budget.builder()
                .budgetId(budgetId)
                .projectId(request.getProjectId())
                .taskId(request.getTaskId())
                .budgetCategory(request.getBudgetCategory())
                .plannedAmount(request.getPlannedAmount())
                .status(BudgetStatus.DRAFT)
                .createdBy(createdBy)
                .actualAmount(java.math.BigDecimal.ZERO)
                .variance(request.getPlannedAmount())
                .isDeleted(false)
                .build();
        Budget savedBudget = budgetRepository.save(budget);
        log.info("Budget created successfully with ID: {} by user: {}", budgetId, createdBy);
        publishBudgetCreatedEvent(savedBudget);
        // Enrich response with project budget summary so the caller can see remaining headroom
        return mapToResponseWithProjectBudget(savedBudget, authorizationHeader);
    }
    /**
     * Builds a BudgetResponse enriched with the project's total / allocated / remaining budget.
     * Called only on create and update so the Finance Officer always sees current headroom.
     * On failure (PM service unreachable) falls back to plain response without project budget fields.
     */
    private BudgetResponse mapToResponseWithProjectBudget(Budget budget, String authorizationHeader) {
        BudgetResponse response = mapToResponse(budget);
        try {
            com.buildsmart.finance.client.dto.ProjectDto project =
                    projectServiceClient.getProject(budget.getProjectId(), authorizationHeader);
            if (project == null || project.budget() == null) return response;
            java.math.BigDecimal projectTotal = java.math.BigDecimal.valueOf(project.budget());
            java.math.BigDecimal allocated = budgetRepository.findAllActiveByProjectId(budget.getProjectId())
                    .stream()
                    .map(b -> b.getPlannedAmount() != null ? b.getPlannedAmount() : java.math.BigDecimal.ZERO)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal remaining = projectTotal.subtract(allocated);
            response.setProjectTotalBudget(projectTotal);
            response.setProjectAllocatedBudget(allocated);
            response.setProjectRemainingBudget(remaining);
            // Warn if >= 80% of project budget is now allocated
            if (projectTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.math.BigDecimal pct = allocated
                        .multiply(new java.math.BigDecimal("100"))
                        .divide(projectTotal, 2, java.math.RoundingMode.HALF_UP);
                if (pct.compareTo(new java.math.BigDecimal("80")) >= 0) {
                    response.setAllocationWarning(String.format(
                            "%.2f%% of the project budget has been allocated. Remaining: %s. " +
                                    "New category budgets may be restricted.",
                            pct.doubleValue(), remaining));
                }
            }
        } catch (Exception e) {
            log.warn("Could not enrich response with project budget info for project {}: {}",
                    budget.getProjectId(), e.getMessage());
        }
        return response;
    }
    @Override
    public BudgetResponse getBudgetById(String budgetId) {
        log.info("Fetching budget with ID: {}", budgetId);
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-001",
                        budgetId,
                        "Budget not found with ID: " + budgetId
                ));
        return mapToResponse(budget);
    }
    @Override
    public PagedResponse<BudgetResponse> getBudgetsByProjectId(String projectId, Pageable pageable) {
        log.info("Fetching budgets for project: {} with pagination", projectId);
        Page<Budget> budgets = budgetRepository.findByProjectId(projectId, pageable);
        return buildPagedResponse(budgets);
    }
    @Override
    public BudgetResponse submitBudgetForApproval(String budgetId) {
        log.info("Submitting budget for approval: {}", budgetId);
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-001",
                        budgetId,
                        "Budget not found with ID: " + budgetId
                ));
        // FEATURE SET 4 — Common approval engine: a budget can be submitted from
        // DRAFT (first time) or REJECTED (resubmission after rework). APPROVED /
        // SUBMITTED / COMPLETED budgets cannot be re-submitted.
        budgetValidator.validateBudgetCanBeSubmitted(budget);
        // Transition CREATED/REJECTED → SUBMITTED so the lifecycle state is visible.
        budget.setStatus(BudgetStatus.SUBMITTED);
        // Clear any prior rejection remarks on resubmission.
        budget.setRejectionReason(null);
        Budget savedBudget = budgetRepository.save(budget);
        // Event is published to notify PM
        publishBudgetSubmittedEvent(savedBudget);
        return mapToResponse(savedBudget);
    }
    @Override
    public BudgetResponse approveBudget(String budgetId, BudgetApprovalRequest request) {
        log.info("Processing budget approval for ID: {} with status: {}", budgetId, request.getStatus());
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-001",
                        budgetId,
                        "Budget not found with ID: " + budgetId
                ));
        budgetValidator.validateBudgetNotApproved(budget);
        // FEATURE SET 4 — Common approval engine: REJECTED MUST carry a non-blank reason.
        if (request.getStatus() == BudgetStatus.REJECTED
                && (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty())) {
            throw new IllegalArgumentException(
                    "rejectionReason is required when rejecting a budget.");
        }
        budget.setStatus(request.getStatus());
        budget.setApprovedBy(request.getApprovedBy());
        budget.setApprovedAt(LocalDateTime.now());
        // Persist rejection remarks for REJECTED budgets so they show up in audit + UI.
        if (request.getStatus() == BudgetStatus.REJECTED) {
            budget.setRejectionReason(request.getRejectionReason());
        }
        Budget updatedBudget = budgetRepository.save(budget);
        log.info("Budget {} approval processed with status: {}", budgetId, request.getStatus());
        // Publish event (BudgetApprovedEvent or BudgetRejectedEvent)
        if (request.getStatus() == BudgetStatus.APPROVED) {
            publishBudgetApprovedEvent(updatedBudget);
        } else {
            publishBudgetRejectedEvent(updatedBudget);
        }
        // NEW FLOW — if this Budget was created from a resource-allocation
        // request, call resource-allocation back so it can auto-create the
        // Allocation and notify the site engineer (or notify the PM on reject).
        notifyResourceAllocationOfBudgetResult(updatedBudget, request);
        return mapToResponse(updatedBudget);
    }
    /**
     * Fire-and-forget callback to resource-allocation for resource-driven budgets.
     * Identified by Budget.referenceResourceId being set.
     */
    private void notifyResourceAllocationOfBudgetResult(Budget budget, BudgetApprovalRequest request) {
        if (budget.getReferenceResourceId() == null || budget.getReferenceResourceId().isBlank()) {
            return; // ordinary PM-created budget — nothing to call back
        }
        if (resourceAllocationCallbackClient == null) {
            log.warn("ResourceAllocationCallbackClient unavailable — cannot notify resource-allocation for resource {}",
                    budget.getReferenceResourceId());
            return;
        }
        String decision = request.getStatus() == BudgetStatus.APPROVED ? "APPROVED" : "REJECTED";
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        payload.put("decision", decision);
        payload.put("budgetId", budget.getBudgetId());
        if ("REJECTED".equals(decision)) {
            payload.put("rejectionReason", request.getRejectionReason());
        }
        try {
            resourceAllocationCallbackClient.notifyBudgetResult(budget.getReferenceResourceId(), payload);
            log.info("Resource-allocation notified of budget result: resourceId={}, decision={}",
                    budget.getReferenceResourceId(), decision);
        } catch (Exception ex) {
            log.warn("Failed to notify resource-allocation for resource {} (decision {}): {}",
                    budget.getReferenceResourceId(), decision, ex.getMessage());
        }
    }
    @Override
    public PagedResponse<BudgetResponse> getBudgetsByStatus(String status, Pageable pageable) {
        log.info("Fetching budgets with status: {}", status);
        BudgetStatus budgetStatus = BudgetStatus.valueOf(status.toUpperCase());
        Page<Budget> budgets = budgetRepository.findByStatus(budgetStatus, pageable);
        return buildPagedResponse(budgets);
    }
    @Override
    public PagedResponse<BudgetResponse> getBudgetsByCreatedBy(String createdBy, Pageable pageable) {
        log.info("Fetching budgets created by: {}", createdBy);
        Page<Budget> budgets = budgetRepository.findByCreatedBy(createdBy, pageable);
        return buildPagedResponse(budgets);
    }
    @Override
    public PagedResponse<BudgetResponse> getBudgetsByProjectIdAndCreatedBy(String projectId, String createdBy, Pageable pageable) {
        log.info("Fetching budgets for project: {} created by: {}", projectId, createdBy);
        Page<Budget> budgets = budgetRepository.findByProjectIdAndCreatedBy(projectId, createdBy, pageable);
        return buildPagedResponse(budgets);
    }
    @Override
    public PagedResponse<BudgetResponse> getBudgetsByStatusAndCreatedBy(String status, String createdBy, Pageable pageable) {
        log.info("Fetching budgets with status: {} created by: {}", status, createdBy);
        BudgetStatus budgetStatus = BudgetStatus.valueOf(status.toUpperCase());
        Page<Budget> budgets = budgetRepository.findByStatusAndCreatedBy(budgetStatus, createdBy, pageable);
        return buildPagedResponse(budgets);
    }
    @Override
    public BudgetResponse updateBudget(String budgetId, BudgetUpdateRequest request) {
        log.info("Updating budget with ID: {}", budgetId);
        // Get budget by ID
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-001",
                        budgetId,
                        "Budget not found with ID: " + budgetId
                ));
        // Validate update request
        budgetValidator.validateBudgetUpdate(request);
        // Validate budget can be edited (only DRAFT status allowed)
        budgetValidator.validateBudgetCanBeEdited(budget);
        // Update only allowed fields
        budget.setBudgetCategory(request.getBudgetCategory());
        budget.setPlannedAmount(request.getPlannedAmount());
        // Recalculate variance if actual amount exists
        if (budget.getActualAmount() != null) {
            budget.setVariance(request.getPlannedAmount().subtract(budget.getActualAmount()));
        } else {
            budget.setVariance(request.getPlannedAmount());
        }
        Budget updatedBudget = budgetRepository.save(budget);
        log.info("Budget updated successfully with ID: {}", budgetId);
        // Show updated project headroom after the planned amount changed
        String authHeader = getAuthorizationHeader();
        if (authHeader != null) {
            return mapToResponseWithProjectBudget(updatedBudget, authHeader);
        }
        return mapToResponse(updatedBudget);
    }
    @Override
    public void deleteBudget(String budgetId) {
        log.info("Deleting budget with ID: {}", budgetId);
        // Get budget by ID
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-001",
                        budgetId,
                        "Budget not found with ID: " + budgetId
                ));
        // Validate budget can be deleted (only DRAFT status allowed)
        budgetValidator.validateBudgetCanBeDeleted(budget);
        // Soft delete
        budget.setIsDeleted(true);
        budgetRepository.save(budget);
        log.info("Budget deleted successfully with ID: {}", budgetId);
    }
    // Helper methods
    private BudgetResponse mapToResponse(Budget budget) {
        return BudgetResponse.builder()
                .budgetId(budget.getBudgetId())
                .projectId(budget.getProjectId())
                .taskId(budget.getTaskId())
                .budgetCategory(budget.getBudgetCategory())
                .plannedAmount(budget.getPlannedAmount())
                .actualAmount(budget.getActualAmount())
                .variance(budget.getVariance())
                .status(budget.getStatus())
                .createdBy(budget.getCreatedBy())
                .approvedBy(budget.getApprovedBy())
                .approvedAt(budget.getApprovedAt())
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }
    private PagedResponse<BudgetResponse> buildPagedResponse(Page<Budget> page) {
        return PagedResponse.<BudgetResponse>builder()
                .content(page.getContent().stream().map(this::mapToResponse).toList())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .timestamp(LocalDateTime.now())
                .build();
    }
    private void publishBudgetCreatedEvent(Budget budget) {
        log.info("Publishing BudgetCreatedEvent for budget: {}", budget.getBudgetId());
        // Event publishing would be implemented via ApplicationEventPublisher
    }
    private void publishBudgetSubmittedEvent(Budget budget) {
        log.info("Publishing BudgetSubmittedEvent for budget: {}", budget.getBudgetId());
        // Push to dedicated notification service so the Project Manager sees a
        // BUDGET_SUBMITTED entry on their dashboard. Fire-and-forget — failures
        // here must NOT roll back the SUBMITTED state.
        // Recipient: the PM (resolved from project.createdBy). If lookup fails,
        // the push is skipped — better than broadcasting to all PMs.
        String pmUserId = resolvePmUserId(budget.getProjectId());
        pushNotification(
                "BUDGET_SUBMITTED",
                String.format(
                        "Budget %s for project %s has been SUBMITTED for approval. " +
                                "Planned amount: %s. Category: %s. Created by: %s.",
                        budget.getBudgetId(),
                        budget.getProjectId(),
                        budget.getPlannedAmount(),
                        budget.getBudgetCategory(),
                        budget.getCreatedBy()),
                budget.getCreatedBy(),          // fromUserId — the finance officer
                "PROJECT_MANAGER",
                pmUserId,                        // toUserId — the project's PM
                budget.getBudgetId());
    }
    private void publishBudgetApprovedEvent(Budget budget) {
        log.info("Publishing BudgetApprovedEvent for budget: {}", budget.getBudgetId());
        // FEATURE SET 6 — push to dedicated notification service. Fire-and-forget.
        // Recipient: the Finance Officer who created the budget.
        pushNotification(
                "BUDGET_APPROVED",
                String.format("Budget %s for project %s has been APPROVED.",
                        budget.getBudgetId(), budget.getProjectId()),
                budget.getApprovedBy(),          // fromUserId — the PM who approved
                "FINANCE_OFFICER",
                budget.getCreatedBy(),           // toUserId — the finance officer
                budget.getBudgetId());
    }
    private void publishBudgetRejectedEvent(Budget budget) {
        log.info("Publishing BudgetRejectedEvent for budget: {}", budget.getBudgetId());
        // FEATURE SET 6 — include rejection reason so the recipient sees what to fix.
        // Recipient: the Finance Officer who created the budget.
        pushNotification(
                "BUDGET_REJECTED",
                String.format("Budget %s for project %s was REJECTED. Reason: %s",
                        budget.getBudgetId(),
                        budget.getProjectId(),
                        budget.getRejectionReason() != null ? budget.getRejectionReason() : "Not provided"),
                budget.getApprovedBy(),          // fromUserId — the PM who rejected
                "FINANCE_OFFICER",
                budget.getCreatedBy(),           // toUserId — the finance officer
                budget.getBudgetId());
    }
    /**
     * Looks up the PM userId for a given projectId via the Project Manager
     * service. Returns null if PM is unreachable or didn't return a createdBy
     * — caller skips the central push rather than broadcasting.
     */
    private String resolvePmUserId(String projectId) {
        if (projectId == null || projectId.isBlank()) return null;
        try {
            String authHeader = getAuthorizationHeader();
            if (authHeader == null) return null;
            ProjectDto project = projectServiceClient.getProject(projectId, authHeader);
            return (project != null) ? project.createdBy() : null;
        } catch (Exception ex) {
            log.warn("Could not resolve PM userId for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }
    /**
     * Helper — fire-and-forget push to the dedicated notification service.
     * toUserId is REQUIRED by the central service. If it is null/blank the push
     * is skipped — never propagated as a 400 to the user's transaction.
     */
    private void pushNotification(String eventType,
                                  String message,
                                  String fromUserId,
                                  String toRole,
                                  String toUserId,
                                  String referenceId) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                    eventType,
                    message,
                    "finance-service",
                    "FINANCE_OFFICER",
                    fromUserId,
                    toRole,
                    toUserId,
                    referenceId,
                    null
            ));
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
    /**
     * Reads the current userId from the Spring Security principal.
     * JwtAuthenticationFilter already called IAM and stored the userId (e.g. "BSFO001")
     * as the principal — this avoids a redundant IAM call and is immune to the
     * JWT key-derivation mismatch between IAM and the finance JwtUtil.
     */
    private String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equals(auth.getName())) {
            log.debug("Resolved userId from SecurityContext: {}", auth.getName());
            return auth.getName();
        }
        return null;
    }
    private String getAuthorizationHeader() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }
            Object credentials = authentication.getCredentials();
            if (credentials == null) {
                return null;
            }
            String token = credentials.toString();
            if (!token.startsWith(BEARER_PREFIX)) {
                token = BEARER_PREFIX + token;
            }
            return token;
        } catch (Exception e) {
            log.error("Error extracting authorization header: {}", e.getMessage());
            return null;
        }
    }
    @Override
    @Transactional(readOnly = true)
    public BudgetUtilizationResponse getBudgetUtilization(String budgetId) {
        log.info("Fetching utilization for budget: {}", budgetId);
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FIN-NOT-FOUND-001", budgetId, "Budget not found with ID: " + budgetId));
        int paymentCount = paymentRepository.findCompletedPaymentsByBudgetId(budgetId).size();
        return toUtilizationResponse(budget, paymentCount);
    }
    @Override
    @Transactional(readOnly = true)
    public ProjectBudgetUtilizationResponse getProjectBudgetUtilization(String projectId) {
        log.info("Fetching budget utilization summary for project: {}", projectId);
        List<Budget> budgets = budgetRepository.findAllActiveByProjectId(projectId);
        List<BudgetUtilizationResponse> utilizations = budgets.stream()
                .map(b -> toUtilizationResponse(b,
                        paymentRepository.findCompletedPaymentsByBudgetId(b.getBudgetId()).size()))
                .toList();
        java.math.BigDecimal totalPlanned = utilizations.stream()
                .map(BudgetUtilizationResponse::getPlannedAmount)
                .filter(a -> a != null)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal totalActual = utilizations.stream()
                .map(BudgetUtilizationResponse::getActualAmount)
                .filter(a -> a != null)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal totalRemaining = totalPlanned.subtract(totalActual);
        java.math.BigDecimal overallPercentage = java.math.BigDecimal.ZERO;
        if (totalPlanned.compareTo(java.math.BigDecimal.ZERO) > 0) {
            overallPercentage = totalActual
                    .multiply(new java.math.BigDecimal("100"))
                    .divide(totalPlanned, 2, java.math.RoundingMode.HALF_UP);
        }
        long approvedCount = budgets.stream()
                .filter(b -> b.getStatus() == com.buildsmart.finance.entity.enums.BudgetStatus.APPROVED)
                .count();
        long overBudgetCount = utilizations.stream()
                .filter(BudgetUtilizationResponse::isOverBudget)
                .count();
        return ProjectBudgetUtilizationResponse.builder()
                .projectId(projectId)
                .totalPlannedAmount(totalPlanned)
                .totalActualAmount(totalActual)
                .totalRemainingAmount(totalRemaining)
                .overallUtilizationPercentage(overallPercentage)
                .totalBudgets(budgets.size())
                .approvedBudgets((int) approvedCount)
                .overBudgetCount((int) overBudgetCount)
                .budgets(utilizations)
                .build();
    }
    private BudgetUtilizationResponse toUtilizationResponse(Budget budget, int paymentCount) {
        java.math.BigDecimal planned = budget.getPlannedAmount() != null
                ? budget.getPlannedAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal actual = budget.getActualAmount() != null
                ? budget.getActualAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal remaining = planned.subtract(actual);
        java.math.BigDecimal utilPct = java.math.BigDecimal.ZERO;
        if (planned.compareTo(java.math.BigDecimal.ZERO) > 0) {
            utilPct = actual
                    .multiply(new java.math.BigDecimal("100"))
                    .divide(planned, 2, java.math.RoundingMode.HALF_UP);
        }
        return BudgetUtilizationResponse.builder()
                .budgetId(budget.getBudgetId())
                .projectId(budget.getProjectId())
                .taskId(budget.getTaskId())
                .budgetCategory(budget.getBudgetCategory())
                .status(budget.getStatus())
                .plannedAmount(planned)
                .actualAmount(actual)
                .remainingAmount(remaining)
                .variance(budget.getVariance() != null ? budget.getVariance() : remaining)
                .utilizationPercentage(utilPct)
                .overBudget(actual.compareTo(planned) > 0)
                .completedPaymentsCount(paymentCount)
                .createdBy(budget.getCreatedBy())
                .approvedBy(budget.getApprovedBy())
                .approvedAt(budget.getApprovedAt())
                .createdAt(budget.getCreatedAt())
                .build();
    }
}