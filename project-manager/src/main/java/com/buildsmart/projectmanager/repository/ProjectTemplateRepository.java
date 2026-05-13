package com.buildsmart.projectmanager.repository;

import com.buildsmart.projectmanager.entity.ProjectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, Long> {
    
    Optional<ProjectTemplate> findByTemplateId(String templateId);
    
    @Query("SELECT DISTINCT t FROM ProjectTemplate t LEFT JOIN FETCH t.milestones WHERE t.templateId = :templateId")
    Optional<ProjectTemplate> findByTemplateIdWithMilestones(String templateId);
    
    List<ProjectTemplate> findByIsActiveTrue();
    
    boolean existsByTemplateId(String templateId);
}
