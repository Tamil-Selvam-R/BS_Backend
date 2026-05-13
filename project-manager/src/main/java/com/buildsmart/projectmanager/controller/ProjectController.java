package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.dto.*;
import com.buildsmart.projectmanager.entity.MilestoneStatus;
import com.buildsmart.projectmanager.entity.TaskStatus;
import com.buildsmart.projectmanager.feign.SiteOpsSiteLogClient;
import com.buildsmart.projectmanager.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Projects", description = "Project Management APIs")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Optional Feign client to SiteOps. When the milestones/progress endpoint
     * is invoked WITHOUT a {@code progressPercent} in the body, the controller
     * uses this client to fetch the latest daily site log for the project and
     * uses its {@code progressPercent} to update milestones. Marked optional so
     * the rest of the controller still works in environments where the bean
     * is not configured (unit tests, isolated dev runs).
     */
    @Autowired(required = false)
    private SiteOpsSiteLogClient siteOpsSiteLogClient;

    @PostMapping
    @Operation(summary = "Create a new project from template")
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createProjectFromTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    @Operation(summary = "Get all projects")
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getProjectById(projectId));
    }

    @GetMapping("/{projectId}/milestones")
    @Operation(summary = "Get project milestones")
    public ResponseEntity<List<MilestoneResponse>> getProjectMilestones(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getProjectMilestones(projectId));
    }

    @PatchMapping("/milestones/{milestoneId}/status")
    @Operation(summary = "Update milestone status")
    public ResponseEntity<MilestoneResponse> updateMilestoneStatus(
            @PathVariable String milestoneId,
            @RequestParam MilestoneStatus status) {
        return ResponseEntity.ok(projectService.updateMilestoneStatus(milestoneId, status));
    }

    /**
     * Recompute milestone statuses from a project's cumulative progress %.
     *
     * <p>Two ways to invoke this endpoint:</p>
     * <ol>
     *   <li><b>Push:</b> caller (typically SiteOps) supplies the percentage
     *       in the body — used by the automatic siteops → PM hop right after
     *       a daily site log is created.
     *       Body: <code>{ "progressPercent": 35.5 }</code></li>
     *   <li><b>Pull (NEW):</b> caller invokes the endpoint with no body
     *       (or with an empty / missing {@code progressPercent}). PM then
     *       fetches the latest daily site log for the project from SiteOps
     *       via Feign and uses its {@code progressPercent} value. This is
     *       useful when you've just approved a site log and want milestones
     *       refreshed without needing to know the percentage.</li>
     * </ol>
     *
     * <p>Distribution logic (unchanged): the percentage is divided into
     * {@code 100 / N} bands across the {@code N} template-defined milestones
     * in order. Milestones whose band is fully behind the progress are
     * COMPLETED, the one containing the progress is IN_PROGRESS, the rest
     * are NOT_STARTED.</p>
     *
     * <p>Returns the full updated milestone list ordered by {@code orderNumber}.</p>
     */
    @PostMapping("/{projectId}/milestones/progress")
    @Operation(summary = "Update milestones from a project's overall progress percentage",
               description = "If the request body has progressPercent, that value is used. "
                       + "Otherwise PM fetches the latest daily site log from SiteOps via Feign "
                       + "and uses its progressPercent to redistribute milestone states.")
    public ResponseEntity<List<MilestoneResponse>> updateMilestonesByProgress(
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, BigDecimal> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        BigDecimal progressPercent = (body != null) ? body.get("progressPercent") : null;

        // Fallback: pull the latest progressPercent straight from SiteOps when
        // the body did not carry it. This is the new behaviour requested so
        // the endpoint can refresh milestones without needing the caller to
        // know the percentage.
        if (progressPercent == null) {
            progressPercent = fetchLatestProgressFromSiteOps(projectId, authorization);
        }
        if (progressPercent == null) {
            throw new IllegalArgumentException(
                    "Could not determine progressPercent for project " + projectId
                    + ". Provide it in the request body, or ensure at least one daily site log "
                    + "exists for this project on SiteOps.");
        }
        return ResponseEntity.ok(
                projectService.updateMilestonesByProgress(projectId, progressPercent));
    }

    /**
     * Calls SiteOps to fetch the latest site log for the project and returns
     * its {@code progressPercent}. Returns {@code null} if SiteOps is
     * unreachable, has no log for the project, or returns a malformed
     * response — the caller turns that into a clear 400.
     */
    private BigDecimal fetchLatestProgressFromSiteOps(String projectId, String authorization) {
        if (siteOpsSiteLogClient == null) {
            log.warn("SiteOpsSiteLogClient bean unavailable — cannot fetch latest progress for project {}",
                    projectId);
            return null;
        }
        try {
            String auth = (authorization != null && !authorization.isBlank())
                    ? authorization
                    : "";
            SiteOpsSiteLogClient.SiteLogDto latest =
                    siteOpsSiteLogClient.getLatestSiteLog(projectId, auth);
            if (latest == null) {
                log.warn("SiteOps returned null for latest site log of project {}", projectId);
                return null;
            }
            log.info("SiteOps reported latest site log {} for project {}: progressPercent={}",
                    latest.logId(), projectId, latest.progressPercent());
            return latest.progressPercent();
        } catch (Exception ex) {
            log.warn("Failed to fetch latest site log for project {} from SiteOps: {}",
                    projectId, ex.getMessage());
            return null;
        }
    }

    @GetMapping("/{projectId}/tasks")
    @Operation(summary = "Get project tasks")
    public ResponseEntity<List<TaskResponse>> getProjectTasks(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getProjectTasks(projectId));
    }

    @PostMapping("/{projectId}/tasks")
    @Operation(summary = "Create a new task for a project")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable String projectId,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = projectService.createTask(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PatchMapping("/tasks/{taskId}/status")
    @Operation(summary = "Update task status")
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @PathVariable String taskId,
            @RequestParam TaskStatus status) {
        return ResponseEntity.ok(projectService.updateTaskStatus(taskId, status));
    }

    // Submission endpoint moved out of PM. Each downstream service
    // (safety / siteops / finance / vendor) now exposes its own submit endpoint
    // and pushes the submission into PM via Feign — see InternalTaskSubmissionController.

    @GetMapping("/tasks/my")
    @Operation(summary = "Get tasks assigned to a specific user",
               description = "Returns all tasks for the given userId — used by the assignee's 'My Tasks' view.")
    public ResponseEntity<List<TaskResponse>> getMyTasks(@RequestParam String userId) {
        return ResponseEntity.ok(projectService.getMyTasks(userId));
    }
}
