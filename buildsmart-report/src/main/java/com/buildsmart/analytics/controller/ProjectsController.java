package com.buildsmart.analytics.controller;

import com.buildsmart.analytics.client.ProjectServiceClient;
import com.buildsmart.analytics.client.ProjectDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
public class ProjectsController {

    private final ProjectServiceClient projectServiceClient;

    public ProjectsController(ProjectServiceClient projectServiceClient) {
        this.projectServiceClient = projectServiceClient;
    }

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getAllProjects() {
        return ResponseEntity.ok(projectServiceClient.getAllProjects());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDTO> getProjectById(@PathVariable("projectId") String projectId) {
        return ResponseEntity.ok(projectServiceClient.getProject(projectId));
    }
}
