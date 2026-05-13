package com.buildsmart.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Slim projection of a project, as returned by the Project Manager service
 * GET /api/projects/{projectId}.
 *
 * createdBy is the PM userId who owns the project — used by Finance services
 * when routing user-targeted notifications (BUDGET_SUBMITTED, EXPENSE_SUBMITTED).
 * Marked nullable in the JSON: older deployments may not include it; we fall
 * back to skipping the central push rather than broadcasting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDto(
        String projectId,
        String projectName,
        Double budget,
        String status,
        String createdBy
) {}