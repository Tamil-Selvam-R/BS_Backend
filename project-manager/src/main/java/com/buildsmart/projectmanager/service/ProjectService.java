package com.buildsmart.projectmanager.service;

import com.buildsmart.projectmanager.dto.*;
import com.buildsmart.projectmanager.entity.*;
import com.buildsmart.projectmanager.exception.*;
import com.buildsmart.projectmanager.repository.*;
import com.buildsmart.projectmanager.feign.FinanceTaskClient;
import com.buildsmart.projectmanager.feign.IamServiceClient;
import com.buildsmart.projectmanager.feign.NotificationServiceClient;
import com.buildsmart.projectmanager.feign.dto.IamAllUsersResponse;
import com.buildsmart.projectmanager.feign.dto.IamUserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectTaskRepository taskRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final TemplateService templateService;
    private final IdGeneratorService idGeneratorService;
    private final NotificationService notificationService;
    private final IamServiceClient iamServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final FinanceTaskClient financeTaskClient;

    @Transactional
    public ProjectResponse createProjectFromTemplate(CreateProjectRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate()) ||
                request.getEndDate().isEqual(request.getStartDate())) {
            throw new InvalidDateRangeException(request.getStartDate(), request.getEndDate());
        }

        ProjectTemplate template = templateService.getTemplateEntityById(request.getTemplateId());

        String projectId = idGeneratorService.generateProjectId();

        if (projectRepository.existsByProjectId(projectId)) {
            throw new DuplicateProjectIdException(projectId);
        }

        Project project = Project.builder()
                .projectId(projectId)
                .projectName(request.getProjectName())
                .description(request.getDescription())
                .template(template)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(ProjectStatus.PLANNING)
                .budget(request.getBudget())
                .createdBy(resolveCreatedBy())
                .build();

        project = projectRepository.save(project);

        createMilestonesFromTemplate(project, template);

        return mapToResponse(project);
    }

    private void createMilestonesFromTemplate(Project project, ProjectTemplate template) {
        log.info("Creating milestones for project {} from template {}. Template has {} milestones.",
                project.getProjectId(), template.getTemplateId(), template.getMilestones().size());

        LocalDate currentDate = project.getStartDate();

        for (TemplateMilestone templateMilestone : template.getMilestones()) {
            LocalDate plannedStartDate = currentDate;
            LocalDate plannedEndDate = currentDate.plusDays(templateMilestone.getEstimatedDurationDays());

            ProjectMilestone milestone = ProjectMilestone.builder()
                    .milestoneId(project.getProjectId() + "-" + templateMilestone.getMilestoneId())
                    .name(templateMilestone.getName())
                    .description(templateMilestone.getDescription())
                    .orderNumber(templateMilestone.getOrderNumber())
                    .status(MilestoneStatus.NOT_STARTED)
                    .plannedStartDate(plannedStartDate)
                    .plannedEndDate(plannedEndDate)
                    .project(project)
                    .build();

            milestoneRepository.save(milestone);
            log.debug("Created milestone: {} - {}", milestone.getMilestoneId(), milestone.getName());
            currentDate = plannedEndDate;
        }

        log.info("Successfully created {} milestones for project {}", template.getMilestones().size(), project.getProjectId());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByCreatedBy(String createdBy) {
        return projectRepository.findByCreatedBy(createdBy).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(String projectId) {
        Project project = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> getProjectMilestones(String projectId) {
        return milestoneRepository.findByProjectProjectIdOrderByOrderNumberAsc(projectId)
                .stream()
                .map(this::mapMilestoneToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getProjectTasks(String projectId) {
        List<ProjectTask> tasks = taskRepository.findByProjectProjectId(projectId);
        return tasks.stream()
                .map(this::mapTaskToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Distributes the project's overall {@code progressPercent} (0–100) across
     * its template-defined milestones, ordered by {@code orderNumber}.
     *
     * <p>For N milestones each milestone covers a {@code 100/N%} band of the
     * project's cumulative progress:</p>
     *
     * <pre>
     *   milestone i (0-indexed): [ i * 100/N, (i+1) * 100/N )
     *
     *   if progress &gt;= upperBound  → milestone COMPLETED  (actualEndDate = now)
     *   else if progress &gt; lowerBound → milestone IN_PROGRESS (actualStartDate = now)
     *   else                         → milestone NOT_STARTED
     * </pre>
     *
     * <p>Idempotent: re-running with the same {@code progressPercent} produces
     * no semantic change. Called by siteops via Feign whenever a Site Engineer
     * submits a daily site log carrying a new {@code progressPercent}.</p>
     *
     * @param projectId       the project whose milestones should be updated
     * @param progressPercent the latest cumulative progress percentage (0–100)
     * @return the updated list of milestones, ordered by {@code orderNumber}
     */
    @Transactional
    public List<MilestoneResponse> updateMilestonesByProgress(String projectId,
                                                              java.math.BigDecimal progressPercent) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required.");
        }
        if (progressPercent == null) {
            throw new IllegalArgumentException("progressPercent is required.");
        }
        // Clamp to [0, 100] defensively even though the SiteLog validator
        // already enforces this — never let bad input corrupt milestone state.
        double progress = progressPercent.doubleValue();
        if (progress < 0d) progress = 0d;
        if (progress > 100d) progress = 100d;

        // Make sure the project exists; throws a clean 404 otherwise.
        projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        List<ProjectMilestone> milestones =
                milestoneRepository.findByProjectProjectIdOrderByOrderNumberAsc(projectId);
        if (milestones.isEmpty()) {
            log.warn("No milestones defined for project {} — nothing to update.", projectId);
            return List.of();
        }

        int n = milestones.size();
        double band = 100.0d / n;
        LocalDate today = LocalDate.now();

        for (int i = 0; i < n; i++) {
            ProjectMilestone m = milestones.get(i);
            double lower = i * band;
            double upper = (i + 1) * band;

            MilestoneStatus newStatus;
            if (progress >= upper) {
                // Whole band is behind us — milestone is done.
                newStatus = MilestoneStatus.COMPLETED;
                if (m.getActualStartDate() == null) m.setActualStartDate(today);
                if (m.getActualEndDate() == null)   m.setActualEndDate(today);
            } else if (progress > lower) {
                // We're inside this band → in progress.
                newStatus = MilestoneStatus.IN_PROGRESS;
                if (m.getActualStartDate() == null) m.setActualStartDate(today);
                m.setActualEndDate(null); // clear in case a previous over-shoot set it
            } else {
                // Haven't reached this band yet.
                newStatus = MilestoneStatus.NOT_STARTED;
                m.setActualStartDate(null);
                m.setActualEndDate(null);
            }
            if (m.getStatus() != newStatus) {
                log.info("Milestone {} ({}): {} → {} (progress={}%)",
                        m.getMilestoneId(), m.getName(), m.getStatus(), newStatus, progress);
                m.setStatus(newStatus);
            }
        }

        milestoneRepository.saveAll(milestones);
        return milestones.stream().map(this::mapMilestoneToResponse).collect(Collectors.toList());
    }

    @Transactional
    public MilestoneResponse updateMilestoneStatus(String milestoneId, MilestoneStatus status) {
        ProjectMilestone milestone = milestoneRepository.findByMilestoneId(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));

        milestone.setStatus(status);

        if (status == MilestoneStatus.IN_PROGRESS && milestone.getActualStartDate() == null) {
            milestone.setActualStartDate(LocalDate.now());
        } else if (status == MilestoneStatus.COMPLETED) {
            milestone.setActualEndDate(LocalDate.now());
        }

        milestone = milestoneRepository.save(milestone);

        // notificationService.notifyMilestoneUpdate(milestone) removed: User entity is no longer present

        return mapMilestoneToResponse(milestone);
    }

    /**
     * Returns tasks assigned to a specific userId — the "My Tasks" view.
     * Called by the frontend when the logged-in user wants to see their work queue.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasks(String userId) {
        return taskRepository.findByAssignedTo(userId)
                .stream()
                .map(this::mapTaskToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Assignee submits their task for approval.
     * Task must be PENDING or IN_PROGRESS to be submittable.
     * Sets status to AWAITING_APPROVAL and creates an ApprovalRequest automatically.
     *
     * @param taskId       the task the assignee is submitting
     * @param description  optional submission note / work summary
     * @return the updated TaskResponse
     */
    @Transactional
    public TaskResponse submitTask(String taskId, String description) {
        ProjectTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() == TaskStatus.AWAITING_APPROVAL) {
            throw new InvalidTaskAssignmentException(
                    "Task already submitted",
                    "Task " + taskId + " is already awaiting approval.");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new InvalidTaskAssignmentException(
                    "Task already completed",
                    "Task " + taskId + " is already completed.");
        }

        // Mark task as submitted (user submitted for approval)
        task.setStatus(TaskStatus.SUBMITTED);
        if (task.getActualStart() == null) {
            task.setActualStart(java.time.LocalDate.now());
        }
        task = taskRepository.save(task);

        // Auto-create the ApprovalRequest so PM can immediately approve/reject.
        // Without this, the task sits in AWAITING_APPROVAL with nothing to approve.
        boolean approvalAlreadyExists = approvalRepository
                .findByProjectProjectIdAndStatus(task.getProject().getProjectId(), ApprovalStatus.PENDING)
                .stream()
                .anyMatch(a -> a.getTask() != null && taskId.equals(a.getTask().getTaskId()));

        if (!approvalAlreadyExists) {
            ApprovalType type = resolveApprovalType(task.getAssignedDepartment());
            String submissionNote = (description != null && !description.isBlank())
                    ? description
                    : "Task submitted for approval by assignee " + task.getAssignedTo();
            ApprovalRequest approval = ApprovalRequest.builder()
                    .approvalId(idGeneratorService.generateApprovalId())
                    .project(task.getProject())
                    .task(task)
                    .approvalType(type)
                    .description(submissionNote)
                    .status(ApprovalStatus.PENDING)
                    .requestedByName(task.getAssignedTo())
                    .requestedByDepartment(task.getAssignedDepartment())
                    .requestedAt(java.time.LocalDateTime.now())
                    .build();
            approvalRepository.save(approval);
            log.info("Auto-created ApprovalRequest {} for task {} (type={})",
                    approval.getApprovalId(), taskId, type);
        }

        // Write an APPROVAL_REQUIRED notification into the PM's own internal notification table
        // so the PM sees it at GET /notifications/me — this is the key entry that was missing.
        try {
            notificationService.notifyTaskSubmitted(task, description);
            log.info("APPROVAL_REQUIRED notification saved for PM (assignedBy={}) for task {}",
                    task.getAssignedBy(), taskId);
        } catch (Exception ex) {
            log.warn("Could not save PM submission notification for task {}: {}", taskId, ex.getMessage());
        }

        // Push APPROVAL_REQUIRED notification to external notification microservice (fire-and-forget)
        // This notifies the PROJECT_MANAGER role that a task needs approval
        try {
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("taskId", task.getTaskId());
            metadata.put("submittedBy", task.getAssignedTo());
            metadata.put("submissionNotes", description != null ? description : "");

            String notificationMessage = String.format(
                    "Task '%s' submitted by %s requires your approval.%s",
                    task.getDescription(),
                    task.getAssignedTo(),
                    (description != null && !description.isBlank()) ? "\nNotes: " + description : ""
            );

            // Route per-user: task.assignedTo = submitter (the executor),
            // task.assignedBy = PM who originally created the task and now needs to approve it.
            // If assignedBy is missing (older rows), the central service rejects null toUserId
            // with @NotBlank, which we accept silently — the PM's own internal notification
            // (saved above via notificationService.notifyTaskSubmitted) covers the gap.
            notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                    "APPROVAL_REQUIRED",
                    notificationMessage,
                    "project-manager",
                    "PROJECT_MANAGER",
                    task.getAssignedTo(),               // fromUserId — the submitter
                    "PROJECT_MANAGER",
                    task.getAssignedBy(),               // toUserId — the PM who owns the task
                    task.getTaskId(),
                    null
            ));

            log.info("APPROVAL_REQUIRED notification sent to notification-service for task: {}", taskId);
        } catch (Exception ex) {
            log.warn("Could not send APPROVAL_REQUIRED notification to notification-service for task {}: {}",
                    taskId, ex.getMessage());
        }

        log.info("Task {} submitted for approval by {}", taskId, task.getAssignedTo());
        return mapTaskToResponse(task);
    }

    private ApprovalType resolveApprovalType(DepartmentCode dept) {
        if (dept == null) return ApprovalType.SITE_WORK;
        return switch (dept) {
            case SAFETY_OFFICER -> ApprovalType.SAFETY;
            case VENDOR -> ApprovalType.VENDOR;
            case FINANCE_OFFICER -> ApprovalType.BUDGET;
            default -> ApprovalType.SITE_WORK;
        };
    }

    @Transactional
    public TaskResponse updateTaskStatus(String taskId, TaskStatus status) {
        ProjectTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        task.setStatus(status);

        if (status == TaskStatus.IN_PROGRESS && task.getActualStart() == null) {
            task.setActualStart(LocalDate.now());
        } else if (status == TaskStatus.COMPLETED) {
            task.setActualEnd(LocalDate.now());
        }

        task = taskRepository.save(task);
        return mapTaskToResponse(task);
    }

    @Transactional
    public TaskResponse createTask(String projectId, CreateTaskRequest request) {
        if (request.getAssignedDepartment() == null) {
            throw new InvalidTaskAssignmentException(
                    "Invalid assigned department",
                    "Assigned department is required and must match a valid department code."
            );
        }

        // Validate assignedTo user exists in IAM
        IamAllUsersResponse usersResponse = iamServiceClient.getAllUsers(null); // null for Authorization, Feign config injects token
        IamUserProfile assignedUser = usersResponse.data().stream()
                .filter(user -> user.userId().equals(request.getAssignedTo()))
                .findFirst()
                .orElse(null);
        if (assignedUser == null) {
            throw new UserNotFoundException(request.getAssignedTo());
        }

        String assignedUserRole = assignedUser.role() == null ? "" : assignedUser.role().toUpperCase(Locale.ROOT);
        if (DepartmentCode.PROJECT_MANAGER.name().equals(assignedUserRole)) {
            throw new InvalidTaskAssignmentException(
                    "Invalid task assignment",
                    "Tasks cannot be assigned to users with role PROJECT_MANAGER."
            );
        }

        if (!request.getAssignedDepartment().name().equals(assignedUserRole)) {
            throw new InvalidTaskAssignmentException(
                    "Invalid task assignment",
                    "Assigned department must match the user's role."
            );
        }

        Project project = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        String taskId = idGeneratorService.generateTaskId(request.getAssignedDepartment());

        ProjectTask task = ProjectTask.builder()
                .taskId(taskId)
                .description(request.getDescription())
                .assignedDepartment(request.getAssignedDepartment())
                .assignedTo(request.getAssignedTo())
                .assignedBy(resolveCreatedBy())
                .plannedStart(request.getPlannedStart())
                .plannedEnd(request.getPlannedEnd())
                .actualStart(request.getActualStart())
                .actualEnd(request.getActualEnd())
                .status(TaskStatus.PENDING)
                .project(project)
                .build();

        task = taskRepository.save(task);
        log.info("Created task {} for project {}", taskId, projectId);

        // Notify the assigned user (internal PM DB notification)
        // This notification is what Finance / Safety / Vendor services pull via their PmNotificationClient.
        notificationService.notifyTaskAssignment(task);

        // Also push to external notification microservice (fire-and-forget)
        try {
            notificationServiceClient.create(new NotificationServiceClient.NotificationPayload(
                    "TASK_ASSIGNED",
                    String.format("You have been assigned task [%s]: %s. Planned: %s to %s.",
                            task.getTaskId(), task.getDescription(), task.getPlannedStart(), task.getPlannedEnd()),
                    "project-service", "PROJECT_MANAGER",
                    task.getAssignedBy(),                                                            // fromUserId — the PM
                    task.getAssignedDepartment() != null ? task.getAssignedDepartment().name() : null,
                    task.getAssignedTo(),                                                            // toUserId — the assignee
                    task.getTaskId(), null));
        } catch (Exception ex) {
            log.warn("Could not push TASK_ASSIGNED notification to notification-service for task '{}': {}",
                    task.getTaskId(), ex.getMessage());
        }

        // If the task is assigned to a FINANCE_OFFICER, trigger Finance's sync endpoint
        // so Finance immediately records the assigned task without waiting for a manual sync.
        // Fire-and-forget: Finance may reject with 403 if called with PM's JWT (role mismatch),
        // but the notification is already saved above — Finance officer can always sync manually.
        if (DepartmentCode.FINANCE_OFFICER.equals(task.getAssignedDepartment())) {
            triggerFinanceTaskSync(task.getTaskId());
        }

        return mapTaskToResponse(task);
    }

    private ProjectResponse mapToResponse(Project project) {
        long totalMilestones = milestoneRepository.countByProjectProjectId(project.getProjectId());
        long completedMilestones = milestoneRepository.countByProjectProjectIdAndStatus(
                project.getProjectId(), MilestoneStatus.COMPLETED);

        long totalTasks = taskRepository.findByProjectProjectId(project.getProjectId()).size();
        long completedTasks = taskRepository.countByProjectProjectIdAndStatus(
                project.getProjectId(), TaskStatus.COMPLETED);

        List<MilestoneResponse> milestones = milestoneRepository
                .findByProjectProjectIdOrderByOrderNumberAsc(project.getProjectId())
                .stream()
                .map(this::mapMilestoneToResponse)
                .collect(Collectors.toList());

        return ProjectResponse.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .description(project.getDescription())
                .templateId(project.getTemplate().getTemplateId())
                .templateName(project.getTemplate().getTemplateName())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .status(project.getStatus().getDisplayName())
                .budget(project.getBudget())
                .createdBy(project.getCreatedBy())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .milestones(milestones)
                .totalMilestones((int) totalMilestones)
                .completedMilestones((int) completedMilestones)
                .totalTasks((int) totalTasks)
                .completedTasks((int) completedTasks)
                .build();
    }

    private MilestoneResponse mapMilestoneToResponse(ProjectMilestone milestone) {
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), milestone.getPlannedEndDate());
        boolean isOverdue = daysRemaining < 0 && milestone.getStatus() != MilestoneStatus.COMPLETED;

        return MilestoneResponse.builder()
                .milestoneId(milestone.getMilestoneId())
                .projectId(milestone.getProject().getProjectId())
                .name(milestone.getName())
                .description(milestone.getDescription())
                .order(milestone.getOrderNumber())
                .status(milestone.getStatus().getDisplayName())
                .plannedStartDate(milestone.getPlannedStartDate())
                .plannedEndDate(milestone.getPlannedEndDate())
                .actualStartDate(milestone.getActualStartDate())
                .actualEndDate(milestone.getActualEndDate())
                .daysRemaining((int) daysRemaining)
                .isOverdue(isOverdue)
                .build();
    }

    private TaskResponse mapTaskToResponse(ProjectTask task) {
        // Populate rejectionReason from the latest REJECTED approval when task is back to PENDING
        String rejectionReason = null;
        if (task.getStatus() == TaskStatus.PENDING) {
            rejectionReason = approvalRepository
                    .findTopByTaskTaskIdAndStatusOrderByApprovedAtDesc(task.getTaskId(), ApprovalStatus.REJECTED)
                    .map(ApprovalRequest::getRejectionReason)
                    .orElse(null);
        }
        return TaskResponse.builder()
                .taskId(task.getTaskId())
                .projectId(task.getProject().getProjectId())
                .description(task.getDescription())
                .assignedDepartment(task.getAssignedDepartment().name())
                .assignedTo(task.getAssignedTo())
                .assignedBy(task.getAssignedBy())
                .plannedStart(task.getPlannedStart())
                .plannedEnd(task.getPlannedEnd())
                .actualStart(task.getActualStart())
                .actualEnd(task.getActualEnd())
                .status(task.getStatus().getDisplayName())
                .rejectionReason(rejectionReason)
                .build();
    }

    /**
     * Calls Finance's POST /api/finance/tasks/sync endpoint as fire-and-forget.
     * Finance uses the Authorization header to identify the assigned officer and
     * pulls the TASK_ASSIGNED notification PM just saved.
     *
     * The call carries the current request's JWT. If Finance rejects it (role
     * mismatch — Finance requires FINANCE_OFFICER), the failure is only logged;
     * the Finance officer can still sync manually via their own portal.
     */
    private void triggerFinanceTaskSync(String taskId) {
        try {
            String authorization = resolveAuthorizationHeader();
            if (authorization != null) {
                FinanceTaskClient.AssignedTaskSyncResult result =
                        financeTaskClient.triggerSync(authorization);
                log.info("Finance task sync triggered for taskId='{}': {} new, {} existed",
                        taskId,
                        result != null ? result.newTasksSynced() : "?",
                        result != null ? result.alreadyExisted() : "?");
            }
        } catch (Exception ex) {
            // Non-blocking — Finance officer can manually call POST /api/finance/tasks/sync
            log.warn("Could not trigger Finance task sync for task '{}': {}",
                    taskId, ex.getMessage());
        }
    }

    /** Reads the Authorization header from the current servlet request. */
    private String resolveAuthorizationHeader() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("Authorization");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveCreatedBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }
}