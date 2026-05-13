package com.buildsmart.siteops.repository;

import com.buildsmart.siteops.entity.SiteLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SiteLogRepository extends JpaRepository<SiteLog, String> {

    Optional<SiteLog> findTopByOrderByLogIdDesc();

    Optional<SiteLog> findTopByApprovalIdNotNullOrderByApprovalIdDesc();

    Optional<SiteLog> findByApprovalId(String approvalId);

    boolean existsByProjectIdAndLogDate(String projectId, LocalDate logDate);

    List<SiteLog> findByProjectIdOrderByLogDateDesc(String projectId);

    Optional<SiteLog> findByProjectIdAndLogDate(String projectId, LocalDate logDate);

    List<SiteLog> findByProjectIdAndLogDateBetweenOrderByLogDateDesc(
            String projectId, LocalDate from, LocalDate to);

    Optional<SiteLog> findTopByProjectIdOrderByLogDateDesc(String projectId);

    // Paginated queries
    Page<SiteLog> findByProjectId(String projectId, Pageable pageable);

    Page<SiteLog> findByProjectIdAndLogDateBetween(
            String projectId, LocalDate from, LocalDate to, Pageable pageable);
}
