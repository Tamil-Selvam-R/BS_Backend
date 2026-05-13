package com.buildsmart.resource_allocation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response Finance returns after accepting a resource budget request.
 * Contains the freshly created Budget id so resource-allocation can store it
 * on the Resource for later reconciliation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceBudgetSubmissionResponseDTO {

    private boolean accepted;
    private String budgetId;
    private String resourceId;
    private String message;
}
