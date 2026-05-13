package com.buildsmart.projectmanager.repository;

import com.buildsmart.projectmanager.entity.ProjectTask;
import com.buildsmart.projectmanager.entity.DepartmentCode;
import com.buildsmart.projectmanager.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    
    Optional<ProjectTask> findByTaskId(String taskId);
    
    boolean existsByTaskId(String taskId);
    
    List<ProjectTask> findByProjectProjectId(String projectId);
    
    List<ProjectTask> findByProjectProjectIdAndAssignedDepartment(String projectId, DepartmentCode departmentCode);
    
    List<ProjectTask> findByAssignedDepartment(DepartmentCode departmentCode);
    
    List<ProjectTask> findByAssignedTo(String assignedTo);
    
    List<ProjectTask> findByStatus(TaskStatus status);
    
    List<ProjectTask> findByProjectProjectIdAndStatus(String projectId, TaskStatus status);
    
    long countByProjectProjectIdAndStatus(String projectId, TaskStatus status);
    
    @Query("SELECT MAX(CAST(SUBSTRING(t.taskId, LENGTH(:prefix) + 1) AS int)) FROM ProjectTask t WHERE t.taskId LIKE :prefix%")
    Integer findMaxTaskIdNumber(@Param("prefix") String prefix);
}
