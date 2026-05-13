package com.buildsmart.projectmanager.repository;

import com.buildsmart.projectmanager.entity.ApprovalRequest;
import com.buildsmart.projectmanager.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    
    Optional<ApprovalRequest> findByApprovalId(String approvalId);

    boolean existsByApprovalId(String approvalId);
    
    List<ApprovalRequest> findByStatus(ApprovalStatus status);
    
    List<ApprovalRequest> findByProjectProjectId(String projectId);
    
    List<ApprovalRequest> findByProjectProjectIdAndStatus(String projectId, ApprovalStatus status);
    
    long countByStatus(ApprovalStatus status);

    /** Latest rejected approval for a task — used to surface rejection reason in TaskResponse. */
    java.util.Optional<ApprovalRequest> findTopByTaskTaskIdAndStatusOrderByApprovedAtDesc(
            String taskId, ApprovalStatus status);
}
