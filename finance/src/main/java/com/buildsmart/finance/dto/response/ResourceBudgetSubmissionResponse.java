package com.buildsmart.finance.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for POST /api/finance/budget/resource-request — tells the
 * resource-allocation service whether the budget request has been accepted
 * (i.e. a Budget row was created in SUBMITTED status) and the budgetId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceBudgetSubmissionResponse {
    private boolean accepted;
    private String budgetId;
    private String resourceId;
    private String message;
}
