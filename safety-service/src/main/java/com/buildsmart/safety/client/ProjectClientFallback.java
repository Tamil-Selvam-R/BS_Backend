package com.buildsmart.safety.client;

import com.buildsmart.safety.client.dto.ProjectDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectClientFallback implements ProjectClient {

    @Override
    public ProjectDto getProject(String projectId, String bearerToken) {
        log.warn("Project service is unavailable — circuit breaker fallback triggered for project {}", projectId);
        return null;
    }

    @Override
    public void updateTaskStatus(String taskId, String status, String bearerToken) {
        // Fire-and-forget: if PM is unavailable we still want the local
        // safety task to be marked COMPLETED. The local DB is the source of
        // truth for the safety officer; PM will reconcile on next sync.
        log.warn("Project service unavailable — could not push status='{}' for task '{}'.", status, taskId);
    }

    @Override
    public void submitTaskForApproval(String taskId, java.util.Map<String, String> body, String bearerToken) {
        // Fire-and-forget: if PM is unavailable, the local task already
        // reflects SUBMITTED. The officer can retry once PM is reachable.
        log.warn("Project service unavailable — could not submit task '{}' for approval.", taskId);
    }
}
