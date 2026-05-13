package com.buildsmart.resource_allocation.repository;

import com.buildsmart.resource_allocation.entities.Allocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, String> {

    List<Allocation> findByProjectId(String projectId);

    List<Allocation> findByResource_ResourceId(String resourceId);

    List<Allocation> findByResource_ResourceIdAndStatus(String resourceId, String status);

    List<Allocation> findByResource_ResourceIdAndProjectIdAndStatus(String resourceId, String projectId, String status);

    @Query("SELECT a.allocationId FROM Allocation a ORDER BY a.allocationId DESC LIMIT 1")
    String findLastAllocationId();

    @Query("SELECT a FROM Allocation a WHERE "
            + "(:projectId IS NULL OR a.projectId = :projectId) AND "
            + "(:resourceId IS NULL OR a.resource.resourceId = :resourceId) AND "
            + "(:status IS NULL OR a.status = :status)")
    Page<Allocation> findWithFilters(
            @Param("projectId") String projectId,
            @Param("resourceId") String resourceId,
            @Param("status") String status,
            Pageable pageable);
}
