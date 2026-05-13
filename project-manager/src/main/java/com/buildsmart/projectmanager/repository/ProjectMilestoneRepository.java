package com.buildsmart.projectmanager.repository;

import com.buildsmart.projectmanager.entity.ProjectMilestone;
import com.buildsmart.projectmanager.entity.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {
    
    Optional<ProjectMilestone> findByMilestoneId(String milestoneId);
    
    List<ProjectMilestone> findByProjectProjectIdOrderByOrderNumberAsc(String projectId);
    
    List<ProjectMilestone> findByProjectProjectIdAndStatus(String projectId, MilestoneStatus status);
    
    long countByProjectProjectIdAndStatus(String projectId, MilestoneStatus status);
    
    long countByProjectProjectId(String projectId);
}
