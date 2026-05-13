package com.buildsmart.resource_allocation.controller;

import com.buildsmart.resource_allocation.dto.ResourceAllocationEventDTO;
import com.buildsmart.resource_allocation.dto.ResourceCostDTO;
import com.buildsmart.resource_allocation.dto.AllocationReportDTO;
import com.buildsmart.resource_allocation.entities.Allocation;
import com.buildsmart.resource_allocation.service.AllocationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Allocation> createAllocation(
            @RequestBody Allocation allocation,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        Allocation savedAllocation = allocationService.createAllocation(allocation, authorizationHeader);
        return ResponseEntity.ok(savedAllocation);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AllocationReportDTO>> getAllAllocations() {
        List<AllocationReportDTO> allocations = allocationService.getAllAllocationReportDTOs();
        return ResponseEntity.ok(allocations);
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Page<Allocation>> getAllAllocationsWithPagination(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "projectId", required = false) String projectId,
            @RequestParam(name = "resourceId", required = false) String resourceId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortDirection", required = false) String sortDirection) {

        Page<Allocation> allocationPage = allocationService.getAllAllocationsWithPagination(
                page, size, projectId, resourceId, status, sortBy, sortDirection);
        return ResponseEntity.ok(allocationPage);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Allocation> getAllocationById(@PathVariable("id") String allocationId) {
        Allocation allocation = allocationService.getAllocationById(allocationId);
        return ResponseEntity.ok(allocation);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Allocation> updateAllocation(@PathVariable("id") String allocationId,
                                                       @RequestBody Allocation allocation,
                                                       @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        Allocation updatedAllocation = allocationService.updateAllocation(allocationId, allocation, authorizationHeader);
        return ResponseEntity.ok(updatedAllocation);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<String> deleteAllocation(@PathVariable("id") String allocationId) {
        allocationService.deleteAllocation(allocationId);
        return ResponseEntity.ok("Allocation deleted successfully.");
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<Allocation>> getAllocationsByProjectId(@PathVariable("projectId") String projectId) {
        List<Allocation> allocations = allocationService.getAllocationsByProjectId(projectId);
        return ResponseEntity.ok(allocations);
    }

    @GetMapping("/resource/{resourceId}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<Allocation>> getAllocationsByResourceId(@PathVariable("resourceId") String resourceId) {
        List<Allocation> allocations = allocationService.getAllocationsByResourceId(resourceId);
        return ResponseEntity.ok(allocations);
    }

    @GetMapping("/{id}/cost")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<ResourceCostDTO> getResourceCostByAllocationId(@PathVariable("id") String allocationId) {
        ResourceCostDTO costDTO = allocationService.getResourceCostByAllocationId(allocationId);
        return ResponseEntity.ok(costDTO);
    }

    @GetMapping("/{id}/event")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<ResourceAllocationEventDTO> getResourceAllocationEvent(@PathVariable("id") String allocationId) {
        ResourceAllocationEventDTO eventDTO = allocationService.getResourceAllocationEvent(allocationId);
        return ResponseEntity.ok(eventDTO);
    }
}
