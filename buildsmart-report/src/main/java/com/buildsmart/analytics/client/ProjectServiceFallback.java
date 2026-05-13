package com.buildsmart.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProjectServiceFallback implements ProjectServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceFallback.class);

    @Override
    public List<ProjectDTO> getAllProjects() {
        log.warn("[Fallback][ProjectService] getAllProjects() — downstream unavailable, returning empty list");
        return List.of();
    }

    @Override
    public ProjectDTO getProject(String projectId) {
        log.warn("[Fallback][ProjectService] getProject({}) — downstream unavailable, returning stub", projectId);
        return new ProjectDTO(
                projectId, "Unavailable", null, null, null, null, null, 
                "UNAVAILABLE", null, null, null, null, 0, 0, 0, 0
        );
    }

    @Override
    public List<MilestoneDTO> getMilestones(String projectId) {
        log.warn("[Fallback][ProjectService] getMilestones({}) — downstream unavailable, returning empty list", projectId);
        return List.of();
    }
}