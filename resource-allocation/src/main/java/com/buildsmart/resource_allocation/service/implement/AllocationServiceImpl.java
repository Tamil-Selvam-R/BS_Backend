package com.buildsmart.resource_allocation.service.implement;

import com.buildsmart.resource_allocation.client.NotificationServiceClient;
import com.buildsmart.resource_allocation.client.ProjectServiceClient;
import com.buildsmart.resource_allocation.client.SiteOpsServiceClient;
import com.buildsmart.resource_allocation.client.dto.IssueDTO;
import com.buildsmart.resource_allocation.client.dto.NotificationCreateRequest;
import com.buildsmart.resource_allocation.client.dto.ProjectDTO;
import com.buildsmart.resource_allocation.client.dto.ResourceAllocatedNotificationDTO;
import com.buildsmart.resource_allocation.dto.ResourceAllocationEventDTO;
import com.buildsmart.resource_allocation.dto.ResourceCostDTO;
import com.buildsmart.resource_allocation.dto.AllocationReportDTO;
import com.buildsmart.resource_allocation.dto.ResourceDTO;
import com.buildsmart.resource_allocation.entities.Allocation;
import com.buildsmart.resource_allocation.entities.Resource;
import com.buildsmart.resource_allocation.repository.AllocationRepository;
import com.buildsmart.resource_allocation.repository.ResourceRepository;
import com.buildsmart.resource_allocation.exception.ResourceNotFoundException;
import com.buildsmart.resource_allocation.exception.BadRequestException;
import com.buildsmart.resource_allocation.exception.ResourceAlreadyAllocatedException;
import com.buildsmart.resource_allocation.utility.IdGeneratorUtil;
import com.buildsmart.resource_allocation.service.AllocationService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AllocationServiceImpl implements AllocationService {

    private static final Logger log = LoggerFactory.getLogger(AllocationServiceImpl.class);
    private static final int WORKING_HOURS_PER_DAY = 8;

    private final AllocationRepository allocationRepository;
    private final ResourceRepository resourceRepository;
    private final ProjectServiceClient projectServiceClient;
    /** Pushes "resource allocated" notification to SiteOps — FEATURE SET 1 step 6. */
    private final SiteOpsServiceClient siteOpsServiceClient;
    /**
     * Pushes allocation events to the dedicated notification-service.
     * Every push must carry a toUserId — the central service rejects rows
     * without one. We resolve the PM's userId from the Project DTO's
     * createdBy field (added in the updated ProjectDTO — see C10a).
     */
    private final NotificationServiceClient notificationServiceClient;

    public AllocationServiceImpl(AllocationRepository allocationRepository,
                                 ResourceRepository resourceRepository,
                                 ProjectServiceClient projectServiceClient,
                                 SiteOpsServiceClient siteOpsServiceClient,
                                 NotificationServiceClient notificationServiceClient) {
        this.allocationRepository = allocationRepository;
        this.resourceRepository = resourceRepository;
        this.projectServiceClient = projectServiceClient;
        this.siteOpsServiceClient = siteOpsServiceClient;
        this.notificationServiceClient = notificationServiceClient;
    }

    @Override
    public Allocation createAllocation(Allocation allocation, String authorizationHeader) {

        if (allocation.getAllocationId() != null && !allocation.getAllocationId().trim().isEmpty()) {
            throw new BadRequestException("Allocation ID is auto-generated. You must not provide an allocationId in the request body.");
        }

        if (allocation.getProjectId() == null || allocation.getProjectId().trim().isEmpty()) {
            throw new BadRequestException("Project ID is required.");
        }

        String projectId = allocation.getProjectId().trim();
        if (!projectId.matches("CHEBS\\d{5}")) {
            throw new BadRequestException("Invalid Project ID format. Project ID must follow the format: CHEBS + 2-digit year + 3-digit number (example: CHEBS26001).");
        }
        allocation.setProjectId(projectId);

        ProjectDTO project = validateProjectExistsViaProjectService(projectId, authorizationHeader);

        // Issue validation — issueId is required and must exist in the project-manager
        if (allocation.getIssueId() == null || allocation.getIssueId().trim().isEmpty()) {
            throw new BadRequestException("Issue ID is required. Allocation must be linked to an existing issue in the project. " +
                    "Fetch available issues from GET /api/projects/issues?projectId=" + projectId);
        }
        validateIssueExistsInProject(projectId, allocation.getIssueId().trim(), authorizationHeader);

        if (allocation.getResource() == null || allocation.getResource().getResourceId() == null) {
            throw new BadRequestException("Resource ID is required.");
        }

        String resourceId = allocation.getResource().getResourceId().trim();

        if (resourceId.isEmpty()) {
            throw new BadRequestException("Resource ID cannot be empty.");
        }

        if (!resourceId.matches("RESBS\\d{3}")) {
            throw new BadRequestException("Invalid Resource ID format. Resource ID must follow the format: RESBS + 3-digit number (example: RESBS001).");
        }

        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            throw new ResourceNotFoundException("Resource not found with ID: " + resourceId);
        }

        if (!resource.getType().equals("Labor") && !resource.getType().equals("Equipment")) {
            throw new BadRequestException("Resource must be of type 'Labor' or 'Equipment' to be allocated.");
        }

        if (resource.getAvailability().equals("On Leave")) {
            throw new BadRequestException("Resource is currently on leave and cannot be allocated.");
        }

        if (resource.getAvailability().equals("Unavailable")) {
            throw new ResourceAlreadyAllocatedException("Resource is not available. Current status: Unavailable.");
        }

        if (allocation.getAssignedDate() == null) {
            throw new BadRequestException("Assigned date is required.");
        }

        if (allocation.getAssignedDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Assigned date cannot be in the past. Please provide today's date or a future date.");
        }

        if (allocation.getReleasedDate() == null) {
            throw new BadRequestException("Released date is required to calculate total cost.");
        }

        if (allocation.getReleasedDate().isBefore(allocation.getAssignedDate())) {
            throw new BadRequestException("Released date cannot be before the assigned date.");
        }

        if (allocation.getReleasedDate().equals(allocation.getAssignedDate())) {
            throw new BadRequestException("Released date cannot be the same as the assigned date. The allocation must be at least 1 day.");
        }

        LocalDate maxReleasedDate = allocation.getAssignedDate().plusYears(1);
        if (allocation.getReleasedDate().isAfter(maxReleasedDate)) {
            throw new BadRequestException("Released date cannot be more than 1 year from the assigned date.");
        }

        validateDatesAgainstProject(project, allocation.getAssignedDate(), allocation.getReleasedDate());

        if (allocation.getStatus() == null || allocation.getStatus().trim().isEmpty()) {
            allocation.setStatus("Active");
        } else {
            String status = allocation.getStatus().trim();
            if (!status.equals("Active") && !status.equals("Released") && !status.equals("Pending")) {
                throw new BadRequestException("Status must be 'Active', 'Released', or 'Pending'.");
            }

            if (status.equals("Released")) {
                throw new BadRequestException("Cannot create a new allocation with 'Released' status.");
            }
            allocation.setStatus(status);
        }

        List<Allocation> duplicateAllocations = allocationRepository
                .findByResource_ResourceIdAndProjectIdAndStatus(resourceId, allocation.getProjectId(), "Active");

        if (!duplicateAllocations.isEmpty()) {
            throw new ResourceAlreadyAllocatedException("This resource is already actively allocated to this project.");
        }

        String lastId = allocationRepository.findLastAllocationId();
        String newId = IdGeneratorUtil.nextAllocationId(lastId);
        allocation.setAllocationId(newId);

        allocation.setResource(resource);

        Double totalCost = calculateTotalCost(resource, allocation.getAssignedDate(), allocation.getReleasedDate());
        resource.setTotalCost(totalCost);
        resourceRepository.save(resource);

        Allocation savedAllocation = allocationRepository.save(allocation);

        if (savedAllocation.getStatus().equals("Active")) {
            resource.setAvailability("Unavailable");
            resourceRepository.save(resource);

            // FEATURE SET 1 step 6 — fire-and-forget notification to SiteOps so
            // the site gets informed that the resource is allocated. SiteOps does
            // NOT need to approve; it only logs the event. Failures here must not
            // roll back the allocation transaction, hence try/catch.
            // We also pass the PM userId (resolved from project.createdBy) so the
            // central notification-service push can route directly to that PM.
            String pmUserId = (project != null) ? project.getCreatedBy() : null;
            notifySiteOpsOfAllocation(savedAllocation, resource, pmUserId, authorizationHeader);
        }

        return savedAllocation;
    }

    /**
     * Fire-and-forget notification to SiteOps when a new allocation becomes Active.
     * Falls back silently if SiteOps is unreachable — fallback class also logs.
     *
     * Also pushes the same event to the dedicated notification-service so the
     * project's PM receives a real notification targeted at their userId.
     *
     * @param pmUserId the project manager's userId (from project.createdBy);
     *                 may be null if the project lookup failed. When null the
     *                 central push is SKIPPED rather than broadcast to all PMs.
     */
    private void notifySiteOpsOfAllocation(Allocation allocation,
                                           Resource resource,
                                           String pmUserId,
                                           String authorizationHeader) {
        // SiteOps internal callback
        try {
            ResourceAllocatedNotificationDTO payload = new ResourceAllocatedNotificationDTO();
            payload.setAllocationId(allocation.getAllocationId());
            payload.setProjectId(allocation.getProjectId());
            payload.setResourceId(resource != null ? resource.getResourceId() : null);
            payload.setResourceType(resource != null ? resource.getType() : null);
            payload.setAssignedDate(allocation.getAssignedDate());
            payload.setReleasedDate(allocation.getReleasedDate());
            payload.setAllocatedBy("resource-allocation-service");
            siteOpsServiceClient.notifyResourceAllocated(payload, authorizationHeader);
            log.info("SiteOps notified of allocation {}", allocation.getAllocationId());
        } catch (Exception ex) {
            log.warn("Could not notify SiteOps of allocation {}: {}",
                    allocation.getAllocationId(), ex.getMessage());
        }
        // Dedicated notification-service push — FEATURE SET 6 — routed to the
        // project's PM by userId. fromUserId is null (system-generated event).
        pushNotification(
                "RESOURCE_ALLOCATED",
                String.format(
                        "Resource %s has been allocated to project %s (allocation %s) from %s to %s.",
                        resource != null ? resource.getResourceId() : "?",
                        allocation.getProjectId(),
                        allocation.getAllocationId(),
                        allocation.getAssignedDate(),
                        allocation.getReleasedDate()),
                "PROJECT_MANAGER",
                pmUserId,
                allocation.getAllocationId(),
                authorizationHeader);
    }

    /**
     * Generic helper — push a single event to the dedicated notification-service.
     * Always non-blocking; failures are logged.
     *
     * toUserId is REQUIRED by the central service. If it's null/blank the push
     * is skipped — better to drop one notification than to spam every PM in
     * the platform.
     */
    private void pushNotification(String eventType,
                                  String message,
                                  String toRole,
                                  String toUserId,
                                  String referenceId,
                                  String authorizationHeader) {
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing — "
                            + "this usually means project.createdBy could not be resolved.",
                    eventType, referenceId);
            return;
        }
        try {
            NotificationCreateRequest req = new NotificationCreateRequest(
                    eventType,
                    message,
                    "resource-allocation-service",
                    "RESOURCE_ALLOCATION",
                    null,                    // fromUserId — system-generated event
                    toRole,
                    toUserId,                // primary routing key
                    referenceId,
                    null
            );
            notificationServiceClient.create(req, authorizationHeader);
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }

    /**
     * Validates that the given issueId exists among the project's issues in the project-manager.
     * Calls GET /api/projects/issues?projectId={projectId} via Feign.
     * If the project-manager is unreachable (fallback returns empty list), validation is skipped
     * with a warning — same degradation strategy used for project validation.
     */
    private void validateIssueExistsInProject(String projectId, String issueId, String authorizationHeader) {
        try {
            List<IssueDTO> issues = projectServiceClient.getIssuesByProjectId(projectId, authorizationHeader);
            if (issues == null || issues.isEmpty()) {
                log.warn("No issues returned for project {} — project-manager may be unreachable. Skipping issue validation.", projectId);
                return;
            }
            boolean found = issues.stream()
                    .anyMatch(issue -> issueId.equals(issue.getIssueId()));
            if (!found) {
                throw new BadRequestException(
                        "Issue '" + issueId + "' does not exist in project '" + projectId + "'. " +
                        "Please provide a valid issueId from GET /api/projects/issues?projectId=" + projectId);
            }
            log.info("Issue {} validated successfully for project {}", issueId, projectId);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not validate issue {} for project {} — project-manager unreachable. Skipping. Error: {}",
                    issueId, projectId, ex.getMessage());
        }
    }

    private ProjectDTO validateProjectExistsViaProjectService(String projectId, String authorizationHeader) {
        try {
            ProjectDTO project = projectServiceClient.getProjectById(projectId, authorizationHeader);
            if (project == null) {
                // Fallback returned null — project service unreachable or circuit open; skip validation
                log.warn("Project service returned null for project ID: {}. Skipping project validation.", projectId);
                return null;
            }
            if (project.getStatus() != null && project.getStatus().equals("Closed")) {
                throw new BadRequestException("Project " + projectId + " is closed. Cannot allocate resources to a closed project.");
            }
            return project;
        } catch (FeignException.NotFound ex) {
            throw new BadRequestException("Project not found with ID: " + projectId + ". Please provide a valid project ID.");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Project service is not available. Skipping project validation for project ID: {}. Error: {}", projectId, ex.getMessage());
            return null;
        }
    }

    private void validateDatesAgainstProject(ProjectDTO project, LocalDate assignedDate, LocalDate releasedDate) {
        if (project == null) {
            return;
        }

        if (project.getStartDate() != null && !project.getStartDate().trim().isEmpty()) {
            try {
                LocalDate projectStartDate = LocalDate.parse(project.getStartDate().trim());
                if (assignedDate != null && assignedDate.isBefore(projectStartDate)) {
                    throw new BadRequestException(
                            "Assigned date (" + assignedDate + ") cannot be before the project start date (" + projectStartDate + ").");
                }
            } catch (BadRequestException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Could not parse project start date: {}. Skipping start date validation.", project.getStartDate());
            }
        }

        if (project.getEndDate() != null && !project.getEndDate().trim().isEmpty()) {
            try {
                LocalDate projectEndDate = LocalDate.parse(project.getEndDate().trim());
                if (releasedDate != null && releasedDate.isAfter(projectEndDate)) {
                    throw new BadRequestException(
                            "Released date (" + releasedDate + ") cannot be after the project end date (" + projectEndDate + ").");
                }
            } catch (BadRequestException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Could not parse project end date: {}. Skipping end date validation.", project.getEndDate());
            }
        }
    }

    @Override
    public List<Allocation> getAllAllocations() {
        return allocationRepository.findAll();
    }

    @Override
    public List<AllocationReportDTO> getAllAllocationReportDTOs() {
        List<Allocation> allocations = allocationRepository.findAll();
        List<AllocationReportDTO> dtoList = new java.util.ArrayList<>();
        for (Allocation allocation : allocations) {
            ResourceDTO resourceDTO = new ResourceDTO();
            Resource res = allocation.getResource();
            if (res != null) {
                resourceDTO.setResourceId(res.getResourceId());
                resourceDTO.setType(res.getType());
                resourceDTO.setAvailability(res.getAvailability());
                resourceDTO.setNumberOfLabors(res.getNumberOfLabors());
                resourceDTO.setSkillLevel(res.getSkillLevel());
                resourceDTO.setEquipmentName(res.getEquipmentName());
                resourceDTO.setEquipmentLevel(res.getEquipmentLevel());
                resourceDTO.setCostPerHour(res.getCostPerHour());
                resourceDTO.setTotalCost(res.getTotalCost());
            }
            AllocationReportDTO dto = new AllocationReportDTO(
                    allocation.getAllocationId(),
                    allocation.getProjectId(),
                    resourceDTO,
                    allocation.getAssignedDate(),
                    allocation.getReleasedDate(),
                    allocation.getStatus()
            );
            dtoList.add(dto);
        }
        return dtoList;
    }

    private void validateAllocationIdFormat(String allocationId) {
        if (allocationId == null || allocationId.trim().isEmpty()) {
            throw new BadRequestException("Allocation ID is required.");
        }
        if (!allocationId.trim().matches("ALCBS\\d{3}")) {
            throw new BadRequestException("Invalid Allocation ID format. Allocation ID must follow the format: ALCBS + 3-digit number (example: ALCBS001).");
        }
    }

    @Override
    public Allocation getAllocationById(String allocationId) {

        validateAllocationIdFormat(allocationId);

        Allocation foundAllocation = allocationRepository.findById(allocationId).orElse(null);
        if (foundAllocation == null) {
            throw new ResourceNotFoundException("Allocation not found with ID: " + allocationId);
        }

        return foundAllocation;
    }

    @Override
    public Allocation updateAllocation(String allocationId, Allocation allocation, String authorizationHeader) {

        validateAllocationIdFormat(allocationId);

        Allocation existingAllocation = allocationRepository.findById(allocationId).orElse(null);
        if (existingAllocation == null) {
            throw new ResourceNotFoundException("Allocation not found with ID: " + allocationId);
        }

        String currentStatus = existingAllocation.getStatus();

        if (currentStatus.equals("Released")) {
            throw new BadRequestException("Cannot update a released allocation.");
        }

        if (allocation.getProjectId() != null && !allocation.getProjectId().trim().isEmpty()) {
            if (currentStatus.equals("Active")) {
                throw new BadRequestException("Cannot change project ID for an active allocation.");
            }
            String newProjectId = allocation.getProjectId().trim();
            if (!newProjectId.matches("CHEBS\\d{5}")) {
                throw new BadRequestException("Invalid Project ID format. Project ID must follow the format: CHEBS + 2-digit year + 3-digit number (example: CHEBS26001).");
            }
            existingAllocation.setProjectId(newProjectId);
        }

        if (allocation.getAssignedDate() != null) {
            if (currentStatus.equals("Active")) {
                throw new BadRequestException("Cannot change assigned date for an active allocation.");
            }
            if (allocation.getAssignedDate().isBefore(LocalDate.now())) {
                throw new BadRequestException("Assigned date cannot be in the past.");
            }
            existingAllocation.setAssignedDate(allocation.getAssignedDate());
        }

        if (allocation.getReleasedDate() != null) {
            LocalDate assignedDate = existingAllocation.getAssignedDate();
            if (allocation.getReleasedDate().isBefore(assignedDate)) {
                throw new BadRequestException("Released date cannot be before the assigned date.");
            }
            if (allocation.getReleasedDate().equals(assignedDate)) {
                throw new BadRequestException("Released date cannot be the same as the assigned date.");
            }
            LocalDate maxReleasedDate = assignedDate.plusYears(1);
            if (allocation.getReleasedDate().isAfter(maxReleasedDate)) {
                throw new BadRequestException("Released date cannot be more than 1 year from the assigned date.");
            }
            existingAllocation.setReleasedDate(allocation.getReleasedDate());
        }

        if (allocation.getAssignedDate() != null || allocation.getReleasedDate() != null) {
            ProjectDTO project = validateProjectExistsViaProjectService(existingAllocation.getProjectId(), authorizationHeader);
            validateDatesAgainstProject(project, existingAllocation.getAssignedDate(), existingAllocation.getReleasedDate());
        }

        if (allocation.getStatus() != null && !allocation.getStatus().trim().isEmpty()) {
            String newStatus = allocation.getStatus().trim();

            if (!newStatus.equals("Active") && !newStatus.equals("Released") && !newStatus.equals("Pending")) {
                throw new BadRequestException("Status must be 'Active', 'Released', or 'Pending'.");
            }

            if (newStatus.equals("Released") && !currentStatus.equals("Released")) {
                if (existingAllocation.getReleasedDate() == null) {
                    existingAllocation.setReleasedDate(LocalDate.now());
                }

                Resource resource = existingAllocation.getResource();
                resource.setAvailability("Available");
                resourceRepository.save(resource);
            }

            if (newStatus.equals("Active") && !currentStatus.equals("Active")) {
                Resource resource = existingAllocation.getResource();

                if (resource.getAvailability().equals("On Leave")) {
                    throw new BadRequestException("Cannot activate allocation. Resource is currently on leave.");
                }

                if (resource.getAvailability().equals("Unavailable")) {
                    throw new ResourceAlreadyAllocatedException("Cannot activate allocation. Resource is already allocated to another project.");
                }

                resource.setAvailability("Unavailable");
                resourceRepository.save(resource);
            }

            existingAllocation.setStatus(newStatus);
        }

        Resource resource = existingAllocation.getResource();
        if (existingAllocation.getAssignedDate() != null && existingAllocation.getReleasedDate() != null) {
            Double totalCost = calculateTotalCost(resource, existingAllocation.getAssignedDate(), existingAllocation.getReleasedDate());
            resource.setTotalCost(totalCost);
            resourceRepository.save(resource);
        }

        return allocationRepository.save(existingAllocation);
    }

    @Override
    public void deleteAllocation(String allocationId) {

        validateAllocationIdFormat(allocationId);

        Allocation allocation = allocationRepository.findById(allocationId).orElse(null);
        if (allocation == null) {
            throw new ResourceNotFoundException("Allocation not found with ID: " + allocationId);
        }

        if (allocation.getStatus().equals("Released")) {
            throw new BadRequestException("Cannot delete a released allocation. It is kept for records.");
        }

        if (allocation.getStatus().equals("Active")) {
            Resource resource = allocation.getResource();
            resource.setAvailability("Available");
            resource.setTotalCost(0.0);
            resourceRepository.save(resource);
        }

        allocationRepository.deleteById(allocationId);
    }

    @Override
    public List<Allocation> getAllocationsByProjectId(String projectId) {

        if (projectId == null || projectId.trim().isEmpty()) {
            throw new BadRequestException("Project ID is required.");
        }

        return allocationRepository.findByProjectId(projectId);
    }

    @Override
    public List<Allocation> getAllocationsByResourceId(String resourceId) {

        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw new BadRequestException("Resource ID is required.");
        }

        if (!resourceId.trim().matches("RESBS\\d{3}")) {
            throw new BadRequestException("Invalid Resource ID format. Resource ID must follow the format: RESBS + 3-digit number (example: RESBS001).");
        }

        boolean exists = resourceRepository.existsById(resourceId);
        if (!exists) {
            throw new ResourceNotFoundException("Resource not found with ID: " + resourceId);
        }

        return allocationRepository.findByResource_ResourceId(resourceId);
    }

    @Override
    public ResourceCostDTO getResourceCostByAllocationId(String allocationId) {

        validateAllocationIdFormat(allocationId);

        Allocation allocation = allocationRepository.findById(allocationId).orElse(null);
        if (allocation == null) {
            throw new ResourceNotFoundException("Allocation not found with ID: " + allocationId);
        }

        Resource resource = allocation.getResource();

        long totalDays = ChronoUnit.DAYS.between(allocation.getAssignedDate(), allocation.getReleasedDate());
        int totalHours = (int) (totalDays * WORKING_HOURS_PER_DAY);

        Double totalCost = calculateTotalCost(resource, allocation.getAssignedDate(), allocation.getReleasedDate());

        ResourceCostDTO costDTO = new ResourceCostDTO();
        costDTO.setAllocationId(allocation.getAllocationId());
        costDTO.setProjectId(allocation.getProjectId());
        costDTO.setResourceId(resource.getResourceId());
        costDTO.setResourceType(resource.getType());
        costDTO.setSkillLevel(resource.getSkillLevel());
        costDTO.setEquipmentName(resource.getEquipmentName());
        costDTO.setEquipmentLevel(resource.getEquipmentLevel());
        costDTO.setNumberOfLabors(resource.getNumberOfLabors());
        costDTO.setCostPerHour(resource.getCostPerHour());
        costDTO.setAssignedDate(allocation.getAssignedDate());
        costDTO.setReleasedDate(allocation.getReleasedDate());
        costDTO.setTotalHours(totalHours);
        costDTO.setTotalCost(totalCost);

        return costDTO;
    }

    @Override
    public ResourceAllocationEventDTO getResourceAllocationEvent(String allocationId) {

        validateAllocationIdFormat(allocationId);

        Allocation allocation = allocationRepository.findById(allocationId).orElse(null);
        if (allocation == null) {
            throw new ResourceNotFoundException("Allocation not found with ID: " + allocationId);
        }

        Resource resource = allocation.getResource();

        Double totalCost = calculateTotalCost(resource, allocation.getAssignedDate(), allocation.getReleasedDate());

        ResourceAllocationEventDTO eventDTO = new ResourceAllocationEventDTO();
        eventDTO.setAllocationId(allocation.getAllocationId());
        eventDTO.setProjectId(allocation.getProjectId());
        eventDTO.setResourceId(resource.getResourceId());
        eventDTO.setResourceType(resource.getType());
        eventDTO.setSkillLevel(resource.getSkillLevel());
        eventDTO.setEquipmentName(resource.getEquipmentName());
        eventDTO.setEquipmentLevel(resource.getEquipmentLevel());
        eventDTO.setNumberOfLabors(resource.getNumberOfLabors());
        eventDTO.setCostPerHour(resource.getCostPerHour());
        eventDTO.setTotalCost(totalCost);
        eventDTO.setAssignedDate(allocation.getAssignedDate());
        eventDTO.setReleasedDate(allocation.getReleasedDate());
        eventDTO.setAllocationStatus(allocation.getStatus());
        eventDTO.setEventType("RESOURCE_ALLOCATED");

        return eventDTO;
    }

    private Double calculateTotalCost(Resource resource, LocalDate assignedDate, LocalDate releasedDate) {
        long totalDays = ChronoUnit.DAYS.between(assignedDate, releasedDate);
        long totalHours = totalDays * WORKING_HOURS_PER_DAY;

        Double costPerHour = resource.getCostPerHour();
        if (costPerHour == null) {
            costPerHour = 0.0;
        }

        String type = resource.getType();

        if (type.equals("Labor")) {
            int numberOfLabors = 1;
            if (resource.getNumberOfLabors() != null) {
                numberOfLabors = resource.getNumberOfLabors();
            }
            return numberOfLabors * costPerHour * totalHours;
        }

        if (type.equals("Equipment")) {
            return costPerHour * totalHours;
        }

        return 0.0;
    }

    @Override
    public Page<Allocation> getAllAllocationsWithPagination(int page, int size,
                                                            String projectId, String resourceId,
                                                            String status,
                                                            String sortBy, String sortDirection) {

        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative.");
        }

        if (size <= 0) {
            throw new BadRequestException("Page size must be greater than 0.");
        }

        if (size > 100) {
            throw new BadRequestException("Page size cannot exceed 100.");
        }

        if (projectId != null && !projectId.trim().isEmpty()) {
            projectId = projectId.trim();
        } else {
            projectId = null;
        }

        if (resourceId != null && !resourceId.trim().isEmpty()) {
            resourceId = resourceId.trim();
        } else {
            resourceId = null;
        }

        if (status != null && !status.trim().isEmpty()) {
            status = status.trim();
            if (!status.equals("Active") && !status.equals("Released") && !status.equals("Pending")) {
                throw new BadRequestException("Status filter must be 'Active', 'Released', or 'Pending'.");
            }
        } else {
            status = null;
        }

        String[] allowedSortFields = {"allocationId", "projectId", "assignedDate", "releasedDate", "status"};

        String resolvedSortBy = "allocationId";
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String trimmedSortBy = sortBy.trim();
            boolean isValid = false;
            for (String field : allowedSortFields) {
                if (field.equals(trimmedSortBy)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                throw new BadRequestException("Invalid sortBy value '" + trimmedSortBy + "'. Allowed values: allocationId, projectId, assignedDate, releasedDate, status.");
            }
            resolvedSortBy = trimmedSortBy;
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (sortDirection != null && !sortDirection.trim().isEmpty()) {
            String trimmedDirection = sortDirection.trim().toLowerCase();
            if (trimmedDirection.equals("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!trimmedDirection.equals("asc")) {
                throw new BadRequestException("Invalid sortDirection value '" + sortDirection.trim() + "'. Allowed values: 'asc' or 'desc'.");
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, resolvedSortBy));
        return allocationRepository.findWithFilters(projectId, resourceId, status, pageable);
    }
}