package com.buildsmart.siteops.repository;

import com.buildsmart.siteops.entity.Issue;
import com.buildsmart.siteops.enums.IssueSeverity;
import com.buildsmart.siteops.enums.IssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, String> {

    Optional<Issue> findTopByOrderByIssueIdDesc();

    Optional<Issue> findTopByApprovalIdNotNullOrderByApprovalIdDesc();

    List<Issue> findByProjectIdOrderByReportedAtDesc(String projectId);

    List<Issue> findByProjectIdAndStatus(String projectId, IssueStatus status);

    List<Issue> findByProjectIdAndSeverity(String projectId, IssueSeverity severity);

    List<Issue> findByProjectIdAndReportedBy(String projectId, String reportedBy);

    List<Issue> findByLogId(String logId);

    long countByProjectIdAndStatus(String projectId, IssueStatus status);

    // Paginated queries
    Page<Issue> findByProjectId(String projectId, Pageable pageable);

    Page<Issue> findByProjectIdAndStatus(String projectId, IssueStatus status, Pageable pageable);

    Page<Issue> findByProjectIdAndSeverity(String projectId, IssueSeverity severity, Pageable pageable);

    Page<Issue> findByProjectIdAndReportedBy(String projectId, String reportedBy, Pageable pageable);
}
