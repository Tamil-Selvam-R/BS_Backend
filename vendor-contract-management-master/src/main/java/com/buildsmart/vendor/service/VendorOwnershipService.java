package com.buildsmart.vendor.service;

import com.buildsmart.vendor.client.ProjectManagerClient;
import com.buildsmart.vendor.client.dto.PMTaskDTO;
import com.buildsmart.vendor.exception.CustomExceptions.ProjectNotAccessibleException;
import com.buildsmart.vendor.exception.CustomExceptions.TaskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cross-module ownership service (Item 5).
 *
 * Enforces that a vendor can only operate on projects and tasks that PM has
 * actually assigned to them. The ground truth lives in PM: a {@code ProjectTask}
 * has an {@code assignedTo} string equal to the vendor's IAM userId.
 *
 * Failure semantics, deliberately:
 *
 *   - PM 404 on the project              → ProjectNotAccessibleException (404 to vendor)
 *   - PM returns no tasks for the vendor → ProjectNotAccessibleException (same wording,
 *                                          to avoid leaking other vendors' projects)
 *   - PM returns the project but the requested taskId is not in the
 *     vendor's filtered list                → TaskNotFoundException (404 to vendor)
 *   - PM unreachable / fallback fires       → check is SKIPPED (logged warn).
 *                                          Same policy as Items 3 and "contract
 *                                          requires PM project": don't punish the
 *                                          vendor for PM outages.
 *
 * One PM call (getProjectTasksTyped) handles both project and task checks for
 * a given write — callers that need both pass them in one go.
 */
@Service
public class VendorOwnershipService {

    private static final Logger log = LoggerFactory.getLogger(VendorOwnershipService.class);

    private final ProjectManagerClient projectManagerClient;

    public VendorOwnershipService(ProjectManagerClient projectManagerClient) {
        this.projectManagerClient = projectManagerClient;
    }

    /**
     * Verify a project is "owned" by this vendor: PM knows the project, AND at
     * least one task in it has assignedTo equal to the vendor's userId.
     *
     * Throws {@link ProjectNotAccessibleException} when PM doesn't know the
     * project, or knows it but has no tasks for this vendor.
     */
    public void requireProjectOwnedByVendor(String projectId, String vendorUserId) {
        if (projectId == null || projectId.isBlank() || vendorUserId == null || vendorUserId.isBlank()) {
            return;
        }

        List<PMTaskDTO> tasks = fetchTasksOrDegrade(projectId);
        if (tasks == null) {
            // PM unreachable — degrade rather than block writes.
            return;
        }

        boolean anyAssigned = tasks.stream()
                .anyMatch(t -> vendorUserId.equals(t.assignedTo()));

        if (!anyAssigned) {
            log.info("Vendor {} has no tasks under project {}; denying access", vendorUserId, projectId);
            throw new ProjectNotAccessibleException(projectId);
        }
    }

    /**
     * Verify both project + task ownership in one call. The taskId must exist
     * in the project AND be assigned to this vendor.
     *
     * Throws {@link ProjectNotAccessibleException} when the project itself
     * isn't accessible to the vendor (no assigned tasks at all). Throws
     * {@link TaskNotFoundException} when the project IS the vendor's, but
     * the specific taskId isn't in their assigned subset.
     */
    public void requireTaskOwnedByVendor(String projectId, String taskId, String vendorUserId) {
        if (projectId == null || projectId.isBlank()
                || taskId == null || taskId.isBlank()
                || vendorUserId == null || vendorUserId.isBlank()) {
            return;
        }

        List<PMTaskDTO> tasks = fetchTasksOrDegrade(projectId);
        if (tasks == null) {
            return;
        }

        // Filter tasks to only those owned by this vendor.
        List<PMTaskDTO> vendorTasks = tasks.stream()
                .filter(t -> vendorUserId.equals(t.assignedTo()))
                .toList();

        if (vendorTasks.isEmpty()) {
            log.info("Vendor {} has no tasks under project {}; denying access", vendorUserId, projectId);
            throw new ProjectNotAccessibleException(projectId);
        }

        boolean taskMatches = vendorTasks.stream()
                .anyMatch(t -> taskId.equals(t.taskId()));

        if (!taskMatches) {
            log.info("Task {} is not in vendor {}'s assigned tasks under project {}; denying access",
                    taskId, vendorUserId, projectId);
            throw new TaskNotFoundException(taskId);
        }
    }

    /**
     * Fetch tasks for a project. Returns:
     *   - empty list   when PM responds 200 with no tasks
     *   - null         when PM is unreachable (fallback factory returned null)
     *
     * On {@link feign.FeignException.NotFound} (project unknown to PM) we
     * treat it as "not accessible" rather than "PM down" — that way
     * {@code requireProjectOwnedByVendor} can throw the right exception.
     */
    private List<PMTaskDTO> fetchTasksOrDegrade(String projectId) {
        try {
            return projectManagerClient.getProjectTasksTyped(projectId);
        } catch (feign.FeignException.NotFound nf) {
            // PM doesn't know the project at all.
            log.info("PM 404 for projectId={}; treating as not accessible", projectId);
            throw new ProjectNotAccessibleException(projectId);
        } catch (feign.FeignException fe) {
            log.warn("PM transport error during ownership check for projectId={}: {}; degrading",
                    projectId, fe.getMessage());
            return null;
        }
    }
}
