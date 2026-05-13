package com.buildsmart.vendor.validator;

import com.buildsmart.vendor.client.ProjectManagerClient;
import com.buildsmart.vendor.client.dto.ProjectDTO;
import com.buildsmart.vendor.exception.CustomExceptions.InvalidDateRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Cross-module validator that enforces every vendor-side activity (contract,
 * delivery, invoice, document) is dated inside its parent project's start /
 * end window.
 *
 * The project window is fetched live from the Project Manager module via
 * {@link ProjectManagerClient#getProjectById(String)}. If PM is unreachable
 * the call returns {@code null} (handled by the Feign fallback) and the
 * validator skips the check rather than blocking the vendor — this keeps the
 * vendor module operational during PM outages while still enforcing the rule
 * whenever PM is up.
 *
 * Rule: a date is valid when {@code startDate <= date <= endDate}. Boundary
 * values are accepted so vendors can submit work on the project's first or
 * last day.
 */
@Component
public class ProjectDateValidator {

    private static final Logger log = LoggerFactory.getLogger(ProjectDateValidator.class);

    private final ProjectManagerClient projectManagerClient;

    public ProjectDateValidator(ProjectManagerClient projectManagerClient) {
        this.projectManagerClient = projectManagerClient;
    }

    /**
     * Ensure a single date falls inside the project window.
     * Used by Invoice (invoice date), Delivery (delivery date), Document
     * (uploadedAt is server-side so this is invoked only when caller passes a
     * date such as a planned delivery).
     */
    public void validateDateWithinProject(String projectId, LocalDate date, String fieldName) {
        if (projectId == null || projectId.isBlank() || date == null) {
            // Caller-side validators already report "required" errors; nothing to do here.
            return;
        }

        ProjectDTO project = fetchProjectQuietly(projectId);
        if (project == null) {
            return;
        }

        LocalDate projectStart = project.startDate();
        LocalDate projectEnd = project.endDate();

        if (projectStart == null || projectEnd == null) {
            log.warn("Project {} returned without start/end dates; skipping date check for {}", projectId, fieldName);
            return;
        }

        if (date.isBefore(projectStart) || date.isAfter(projectEnd)) {
            throw new InvalidDateRangeException(
                    fieldName + " (" + date + ") must fall within project " + projectId
                            + " window [" + projectStart + " to " + projectEnd + "]");
        }
    }

    /**
     * Ensure both start and end of a sub-period (e.g. a contract span) fall
     * inside the project window.
     */
    public void validateRangeWithinProject(String projectId, LocalDate start, LocalDate end) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }

        ProjectDTO project = fetchProjectQuietly(projectId);
        if (project == null) {
            return;
        }

        LocalDate projectStart = project.startDate();
        LocalDate projectEnd = project.endDate();

        if (projectStart == null || projectEnd == null) {
            log.warn("Project {} returned without start/end dates; skipping range check", projectId);
            return;
        }

        if (start != null && (start.isBefore(projectStart) || start.isAfter(projectEnd))) {
            throw new InvalidDateRangeException(
                    "Start date (" + start + ") must fall within project " + projectId
                            + " window [" + projectStart + " to " + projectEnd + "]");
        }
        if (end != null && (end.isBefore(projectStart) || end.isAfter(projectEnd))) {
            throw new InvalidDateRangeException(
                    "End date (" + end + ") must fall within project " + projectId
                            + " window [" + projectStart + " to " + projectEnd + "]");
        }
    }

    /**
     * Fetch the project from PM, swallowing transport / 404 errors. Returns
     * {@code null} when the project cannot be retrieved so callers degrade
     * gracefully (rule is enforced when PM is up, skipped when PM is down).
     */
    private ProjectDTO fetchProjectQuietly(String projectId) {
        try {
            return projectManagerClient.getProjectById(projectId);
        } catch (Exception e) {
            log.warn("Could not fetch project {} from PM for date validation: {}", projectId, e.getMessage());
            return null;
        }
    }
}
