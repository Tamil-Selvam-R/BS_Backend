package com.buildsmart.analytics.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

/**
 * Feign client for the Project Manager microservice.
 * Eureka-registered name: "project-service" (project-manager/src/main/resources/application.properties).
 * Project-manager has server.servlet.context-path=/api, so the controller's "/projects" mapping
 * is exposed as "/api/projects" on the wire — which is exactly what this client targets.
 */
@FeignClient(name = "project-service", fallback = ProjectServiceFallback.class, url = "${project.service.url}")
public interface ProjectServiceClient {

    @GetMapping("/api/projects")
    List<ProjectDTO> getAllProjects();

    @GetMapping("/api/projects/{projectId}")
    ProjectDTO getProject(@PathVariable("projectId") String projectId);

    @GetMapping("/api/projects/{projectId}/milestones")
    List<MilestoneDTO> getMilestones(@PathVariable("projectId") String projectId);
}