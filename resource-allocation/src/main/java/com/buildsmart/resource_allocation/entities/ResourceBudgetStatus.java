package com.buildsmart.resource_allocation.entities;

/**
 * Lifecycle of a Resource w.r.t. its Finance budget approval.
 *
 * Flow:
 *   NONE             -> default for legacy rows / resources never sent for budget
 *   PENDING_BUDGET   -> sent to Finance, awaiting decision
 *   BUDGET_APPROVED  -> Finance approved; an Allocation can now be created automatically
 *   BUDGET_REJECTED  -> Finance rejected; rejection reason is stored on the Resource
 */
public enum ResourceBudgetStatus {
    NONE,
    PENDING_BUDGET,
    BUDGET_APPROVED,
    BUDGET_REJECTED
}
