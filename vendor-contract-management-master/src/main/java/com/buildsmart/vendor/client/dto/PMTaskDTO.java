package com.buildsmart.vendor.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Typed projection of PM's {@code TaskResponse}. We only deserialise the
 * fields needed for ownership checks in the vendor module — Jackson ignores
 * the rest. Used by Item 5 to verify whether a (projectId, taskId) pair is
 * actually assigned to the authenticated vendor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PMTaskDTO(
        String taskId,
        String projectId,
        String assignedTo,
        String assignedDepartment,
        String status
) {}
