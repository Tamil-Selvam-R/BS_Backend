package com.buildsmart.siteops.service;

import com.buildsmart.siteops.dto.IssueRequest;
import com.buildsmart.siteops.dto.IssueResponse;
import com.buildsmart.siteops.dto.IssueUpdateRequest;
import com.buildsmart.siteops.dto.PaginatedResponse;
import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.IssueStatus;

import java.util.List;

public interface IssueService {

    IssueResponse createIssue(IssueRequest request, String reportedBy, String authorization);

    IssueResponse getIssueById(String issueId);

    List<IssueResponse> getIssuesByProject(String projectId);

    List<IssueResponse> getIssuesByProjectAndStatus(String projectId, IssueStatus status);

    List<IssueResponse> getIssuesByProjectAndSeverity(String projectId, IssueSeverity severity);

    List<IssueResponse> getIssuesByProjectAndReporter(String projectId, String reportedBy);

    List<IssueResponse> getIssuesByLogId(String logId);

    /**
     * PM assigns, updates status, or closes an issue.
     * Only PROJECT_MANAGER or ADMIN role may call this.
     */
    IssueResponse updateIssue(String issueId, IssueUpdateRequest request);

    // Paginated methods
    PaginatedResponse<IssueResponse> getIssuesByProjectPaginated(
            String projectId, int pageNumber, int pageSize, String sortBy, String sortDirection);

    PaginatedResponse<IssueResponse> getIssuesByProjectAndStatusPaginated(
            String projectId, IssueStatus status, int pageNumber, int pageSize, String sortBy, String sortDirection);

    PaginatedResponse<IssueResponse> getIssuesByProjectAndSeverityPaginated(
            String projectId, IssueSeverity severity, int pageNumber, int pageSize, String sortBy, String sortDirection);

    PaginatedResponse<IssueResponse> getIssuesByProjectAndReporterPaginated(
            String projectId, String reportedBy, int pageNumber, int pageSize, String sortBy, String sortDirection);
}
