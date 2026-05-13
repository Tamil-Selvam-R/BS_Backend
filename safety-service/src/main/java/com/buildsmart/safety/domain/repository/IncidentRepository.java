package com.buildsmart.safety.domain.repository;

import com.buildsmart.safety.domain.model.Incident;
import com.buildsmart.safety.domain.model.IncidentSeverity;
import com.buildsmart.safety.domain.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface IncidentRepository
        extends JpaRepository<Incident, String>, JpaSpecificationExecutor<Incident> {

    List<Incident> findByProjectId(String projectId);
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findBySeverity(IncidentSeverity severity);
    Incident findTopByOrderByIncidentIdDesc();
}
