package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.IssueDTO;
import com.buildsmart.resource_allocation.client.dto.ProjectDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ProjectServiceClientFallback implements ProjectServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceClientFallback.class);

    @Override
    public ProjectDTO getProjectById(String projectId, String authorization) {
        log.warn("[Fallback][ProjectService] getProjectById({}) - Project service is unavailable. Returning null so allocation proceeds with warning.", projectId);
        return null;
    }

    @Override
    public List<IssueDTO> getIssuesByProjectId(String projectId, String authorization) {
        log.warn("[Fallback][ProjectService] getIssuesByProjectId({}) - Project service is unavailable. Returning empty list — issue validation will be skipped.", projectId);
        return Collections.emptyList();
    }
}

