package com.buildsmart.projectmanager.repository;

import com.buildsmart.projectmanager.entity.Project;
import com.buildsmart.projectmanager.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    Optional<Project> findByProjectId(String projectId);
    
    boolean existsByProjectId(String projectId);
    
    List<Project> findByStatus(ProjectStatus status);
    
    @Query("SELECT MAX(CAST(SUBSTRING(p.projectId, 8) AS int)) FROM Project p WHERE p.projectId LIKE :prefix%")
    Integer findMaxProjectIdNumber(String prefix);
    
    @Query("SELECT p FROM Project p JOIN FETCH p.template WHERE p.projectId = :projectId")
    Optional<Project> findByProjectIdWithTemplate(String projectId);
}
