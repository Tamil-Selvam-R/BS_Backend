package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.IssueDTO;
import com.buildsmart.resource_allocation.client.dto.ProjectDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "project-service", url = "${project.service.url}", fallback = ProjectServiceClientFallback.class)
public interface ProjectServiceClient {

    @GetMapping("/projects/{projectId}")
    ProjectDTO getProjectById(
            @PathVariable("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);

    /**
     * Fetches all issues for a project from the project-manager service.
     * Maps to GET /api/projects/issues?projectId={projectId}
     * Used to validate that the issueId on an allocation actually exists in the project.
     */
    @GetMapping("/projects/issues")
    List<IssueDTO> getIssuesByProjectId(
            @RequestParam("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);
}

