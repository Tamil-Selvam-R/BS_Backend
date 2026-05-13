package com.buildsmart.resource_allocation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response from Finance's
 *   GET /api/finance/budget/status?projectId=…&amp;resourceId=…
 * endpoint. Resource-allocation uses this just before creating an allocation
 * to confirm Finance has approved the budget for the (project, resource)
 * pair — replacing the broken notification-based design.
 *
 * <ul>
 *   <li>{@code found=false}                 — no budget request exists yet</li>
 *   <li>{@code found=true, approved=true}   — budget APPROVED, allocation can proceed</li>
 *   <li>{@code found=true, approved=false}  — budget is SUBMITTED / DRAFT / REJECTED</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetStatusResponseDTO {

    private String projectId;
    private String resourceId;
    private boolean found;
    private boolean approved;
    private String budgetId;
    private String status;             // APPROVED / REJECTED / SUBMITTED / DRAFT / COMPLETED / null
    private String rejectionReason;
    private String message;
}
