package com.buildsmart.safety.domain.repository;

import com.buildsmart.safety.domain.model.AssignedTask;
import com.buildsmart.safety.domain.model.AssignedTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignedTaskRepository extends JpaRepository<AssignedTask, String> {

    /** Used to skip already-synced notifications from PM. */
    boolean existsByPmNotificationId(String pmNotificationId);

    /** Used to skip duplicate PM task IDs. */
    boolean existsByPmTaskId(String pmTaskId);

    /** All tasks for a given officer. */
    List<AssignedTask> findByAssignedToOrderBySyncedAtDesc(String assignedTo);

    /** Tasks for an officer filtered by status (PENDING / COMPLETED). */
    List<AssignedTask> findByAssignedToAndStatusOrderBySyncedAtDesc(String assignedTo, AssignedTaskStatus status);

    /** All tasks for a specific project (any officer). */
    List<AssignedTask> findByProjectIdOrderBySyncedAtDesc(String projectId);

    /** Tasks for a specific officer on a specific project. */
    List<AssignedTask> findByAssignedToAndProjectId(String assignedTo, String projectId);

    /** Find by PM task ID. */
    Optional<AssignedTask> findByPmTaskId(String pmTaskId);

    /** Newest ID for ID generation. */
    AssignedTask findTopByOrderByIdDesc();
}
