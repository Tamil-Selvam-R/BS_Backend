package com.buildsmart.resource_allocation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the Issue response returned by the project-manager service
 * at GET /api/projects/issues?projectId={projectId}.
 * Only the fields needed for allocation validation are mapped here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueDTO {

    private String issueId;
    private String projectId;
    private String status;
    private String resourceType;
    private String description;

    public IssueDTO() {}

    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
