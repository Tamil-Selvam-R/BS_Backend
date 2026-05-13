package com.buildsmart.safety.domain.repository;

import com.buildsmart.safety.domain.model.InspectionStatus;
import com.buildsmart.safety.domain.model.InspectionType;
import com.buildsmart.safety.domain.model.SafetyInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SafetyInspectionRepository
        extends JpaRepository<SafetyInspection, String>, JpaSpecificationExecutor<SafetyInspection> {

    List<SafetyInspection> findByProjectId(String projectId);
    List<SafetyInspection> findByStatus(InspectionStatus status);
    SafetyInspection findTopByOrderByInspectionIdDesc();

    /** Blocks same inspection type being active on the same project today (any officer). */
    boolean existsByProjectIdAndDateAndInspectionTypeAndStatusIn(
            String projectId, LocalDate date,
            InspectionType inspectionType,
            Collection<InspectionStatus> activeStatuses);
}
