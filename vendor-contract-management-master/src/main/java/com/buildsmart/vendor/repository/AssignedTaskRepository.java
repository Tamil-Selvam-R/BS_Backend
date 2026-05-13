package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.entity.AssignedTask;
import com.buildsmart.vendor.enums.AssignedTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignedTaskRepository extends JpaRepository<AssignedTask, String> {

    boolean existsByPmNotificationId(String pmNotificationId);

    boolean existsByPmTaskId(String pmTaskId);

    List<AssignedTask> findByAssignedToOrderBySyncedAtDesc(String assignedTo);

    List<AssignedTask> findByAssignedToAndStatusOrderBySyncedAtDesc(String assignedTo, AssignedTaskStatus status);

    List<AssignedTask> findByProjectIdOrderBySyncedAtDesc(String projectId);

    List<AssignedTask> findByAssignedToAndProjectId(String assignedTo, String projectId);

    Optional<AssignedTask> findByPmTaskId(String pmTaskId);

    AssignedTask findTopByOrderByIdDesc();
}
