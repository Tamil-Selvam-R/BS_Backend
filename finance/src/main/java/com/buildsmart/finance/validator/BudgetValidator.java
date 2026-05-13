package com.buildsmart.finance.validator;

import java.math.BigDecimal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.buildsmart.finance.client.ProjectClient;
import com.buildsmart.finance.client.dto.ProjectDto;
import com.buildsmart.finance.dto.request.BudgetCreateRequest;
import com.buildsmart.finance.dto.request.BudgetUpdateRequest;
import com.buildsmart.finance.entity.Budget;
import com.buildsmart.finance.exception.BusinessRuleException;
import com.buildsmart.finance.exception.ValidationException;
import com.buildsmart.finance.repository.BudgetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetValidator {

    private final BudgetRepository budgetRepository;
    private final ProjectClient projectServiceClient;
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Validate budget creation request
     */
    public void validateBudgetCreation(BudgetCreateRequest request) {
        log.info("Validating budget creation request for project: {}", request.getProjectId());

        // Validate project exists
        validateProjectExists(request.getProjectId());

        // Validate planned amount
        if (request.getPlannedAmount() == null || request.getPlannedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "FIN-VAL-003",
                    "plannedAmount",
                    "Planned amount must be greater than 0"
            );
        }

        // Check for duplicate budget (same project + category)
        boolean exists = budgetRepository.existsByProjectIdAndCategoryAndIsDeletedFalse(
                request.getProjectId(),
                request.getBudgetCategory()
        );

        if (exists) {
            throw new BusinessRuleException(
                    "FIN-BUS-002",
                    "A budget already exists for project " + request.getProjectId() +
                            " with category " + request.getBudgetCategory().getDisplayName()
            );
        }

        // Validate budget amount doesn't exceed project budget
       validateBudgetAmountNotExceedingProjectBudget(request.getProjectId(), request.getPlannedAmount());
    }

    /**
     * Validate that project exists in Project Manager service
     */
    private void validateProjectExists(String projectId) {
        try {
            String authHeader = getAuthorizationHeader();
            if (authHeader == null) {
                log.warn("No authorization header for project validation");
                throw new BusinessRuleException(
                        "AUTH-ERROR",
                        "Authentication required to validate project"
                );
            }

            log.info("Validating project existence: {}", projectId);
            ProjectDto project = projectServiceClient.getProject(projectId, authHeader);

            if (project == null) {
                throw new BusinessRuleException(
                        "PROJECT-NOT-FOUND",
                        "Project with ID " + projectId + " does not exist"
                );
            }

            log.info("Project {} validated successfully", projectId);

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating project {}: {}", projectId, e.getMessage());
            throw new BusinessRuleException(
                    "PROJECT-VALIDATION-ERROR",
                    "Could not validate project " + projectId + ". Please try again."
            );
        }
    }

    /**
     * Validate that budget amount doesn't exceed the project's total budget
     */
   private void validateBudgetAmountNotExceedingProjectBudget(String projectId, BigDecimal requestedAmount) {
       try {
           String authHeader = getAuthorizationHeader();
           if (authHeader == null) {
               return;
           }

           ProjectDto project = projectServiceClient.getProject(projectId, authHeader);
           if (project == null) {
               throw new BusinessRuleException("PROJECT-NOT-FOUND",
                       "Project with ID " + projectId + " does not exist");
           }

           Double projectBudget = project.budget();
           if (projectBudget == null || projectBudget <= 0) {
               log.warn("Project {} has no budget defined — skipping limit check", projectId);
               return;
           }

           BigDecimal projectBudgetBD = BigDecimal.valueOf(projectBudget);

           // Sum all active (non-deleted) category budgets already created for this project
           BigDecimal alreadyAllocated = budgetRepository.findAllActiveByProjectId(projectId)
                   .stream()
                   .map(b -> b.getPlannedAmount() != null ? b.getPlannedAmount() : BigDecimal.ZERO)
                   .reduce(BigDecimal.ZERO, BigDecimal::add);

           BigDecimal totalAfterCreation = alreadyAllocated.add(requestedAmount);

           // Hard limit: the sum of ALL category budgets must not exceed the project's total budget
           if (totalAfterCreation.compareTo(projectBudgetBD) > 0) {
               throw new BusinessRuleException(
                       "FIN-BUS-006",
                       String.format(
                           "Cannot create this budget. Adding %s would bring total category allocation to %s, " +
                           "which exceeds the project's total budget of %s. " +
                           "Already allocated: %s. Available: %s.",
                           requestedAmount, totalAfterCreation, projectBudgetBD,
                           alreadyAllocated, projectBudgetBD.subtract(alreadyAllocated))
               );
           }

           // Soft warning: log when >= 80% of project budget is allocated
           BigDecimal pct = totalAfterCreation
                   .multiply(new BigDecimal("100"))
                   .divide(projectBudgetBD, 2, java.math.RoundingMode.HALF_UP);
           if (pct.compareTo(new BigDecimal("80")) >= 0) {
               log.warn("Project {} budget alert: {}% allocated after this budget (total={}, project budget={})",
                       projectId, pct, totalAfterCreation, projectBudgetBD);
           }

           log.info("Budget limit check passed for project {}: new total={}/{} ({}%)",
                   projectId, totalAfterCreation, projectBudgetBD, pct);

       } catch (BusinessRuleException e) {
           throw e;
       } catch (Exception e) {
           log.error("Error validating budget amount for project {}: {}", projectId, e.getMessage());
           log.warn("Skipping budget amount validation due to error");
       }
   }

    /**
     * Extract authorization header from Security Context
     */
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

    /**
     * Validate that budget is in draft status before allowing edits.
     * FEATURE SET 4 — also allow REJECTED so the user can rework and resubmit.
     */
    public void validateBudgetCanBeEdited(Budget budget) {
        String s = budget.getStatus().name();
        if (!s.equals("DRAFT") && !s.equals("REJECTED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-003",
                    "Budget cannot be edited in status " + s
                    + ". Only DRAFT or REJECTED budgets can be edited."
            );
        }
    }

    /**
     * FEATURE SET 4 — Common approval engine.
     * A budget can be submitted from DRAFT (first time) or REJECTED (resubmission).
     */
    public void validateBudgetCanBeSubmitted(Budget budget) {
        String s = budget.getStatus().name();
        if (!s.equals("DRAFT") && !s.equals("REJECTED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-008",
                    "Budget cannot be submitted in status " + s
                    + ". Only DRAFT or REJECTED budgets can be submitted for approval."
            );
        }
    }

    /**
     * Validate that budget is not already approved
     */
    public void validateBudgetNotApproved(Budget budget) {
        if (budget.getStatus().name().equals("APPROVED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-004",
                    "Budget is already approved and cannot be modified"
            );
        }
    }

    /**
     * Validate budget update request
     */
    public void validateBudgetUpdate(BudgetUpdateRequest request) {
        log.info("Validating budget update request");

        // Validate planned amount
        if (request.getPlannedAmount() == null || request.getPlannedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "FIN-VAL-011",
                    "plannedAmount",
                    "Planned amount must be greater than 0"
            );
        }

        // Validate category
        if (request.getBudgetCategory() == null) {
            throw new ValidationException(
                    "FIN-VAL-012",
                    "budgetCategory",
                    "Budget category is required"
            );
        }
    }

    /**
     * Validate that budget can be deleted (only DRAFT status allowed)
     */
    public void validateBudgetCanBeDeleted(Budget budget) {
        if (!budget.getStatus().name().equals("DRAFT")) {
            throw new BusinessRuleException(
                    "FIN-BUS-007",
                    "Budget cannot be deleted after approval. Current status: " + budget.getStatus()
            );
        }
    }
}
