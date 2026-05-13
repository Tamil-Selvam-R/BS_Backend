package com.buildsmart.finance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Synchronous budget approval check response used by Resource Allocation
 * service. The shape MUST stay aligned with the resource-allocation
 * BudgetApprovalResponseDTO so Feign deserialization succeeds.
 *
 * Added for FEATURE SET 1 — synchronous budget validation before allocation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetApprovalCheckResponse {

    /** True when the requested totalCost fits within the project's available budget. */
    private boolean approved;
    /** Human-readable reason — populated for both approve and reject so callers can log it. */
    private String message;
    private String projectId;
    private String allocationId;
    /** When approved, the cost amount that was reserved/approved. */
    private Double approvedAmount;
}
