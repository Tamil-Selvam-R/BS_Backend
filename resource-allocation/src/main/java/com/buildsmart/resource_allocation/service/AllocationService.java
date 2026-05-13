package com.buildsmart.resource_allocation.service;

import com.buildsmart.resource_allocation.entities.Allocation;
import com.buildsmart.resource_allocation.dto.ResourceCostDTO;
import com.buildsmart.resource_allocation.dto.ResourceAllocationEventDTO;
import com.buildsmart.resource_allocation.dto.AllocationReportDTO;
import org.springframework.data.domain.Page;
import java.util.List;

public interface AllocationService {

    Allocation createAllocation(Allocation allocation, String authorizationHeader);

    List<Allocation> getAllAllocations();

    List<AllocationReportDTO> getAllAllocationReportDTOs();

    Allocation getAllocationById(String allocationId);

    Allocation updateAllocation(String allocationId, Allocation allocation, String authorizationHeader);

    void deleteAllocation(String allocationId);

    List<Allocation> getAllocationsByProjectId(String projectId);

    List<Allocation> getAllocationsByResourceId(String resourceId);

    Page<Allocation> getAllAllocationsWithPagination(int page, int size,
                                                     String projectId, String resourceId,
                                                     String status,
                                                     String sortBy, String sortDirection);

    ResourceCostDTO getResourceCostByAllocationId(String allocationId);

    ResourceAllocationEventDTO getResourceAllocationEvent(String allocationId);
}
