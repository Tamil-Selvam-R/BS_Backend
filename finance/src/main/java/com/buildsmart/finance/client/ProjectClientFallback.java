package com.buildsmart.finance.client;

import com.buildsmart.finance.client.dto.ProjectDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectClientFallback implements ProjectClient {

    @Override
    public ProjectDto getProject(String projectId, String authHeader) {
        log.warn("Project service is unavailable — circuit breaker fallback triggered for project {}", projectId);
        // Return null so resolveProject() throws ResourceNotFoundException cleanly
        return null;
    }
}
