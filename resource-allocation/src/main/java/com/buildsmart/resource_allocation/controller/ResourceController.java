package com.buildsmart.resource_allocation.controller;

import com.buildsmart.resource_allocation.entities.Resource;
import com.buildsmart.resource_allocation.dto.ResourceDTO;
import com.buildsmart.resource_allocation.service.ResourceService;
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
import java.util.List;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Resource> addResource(@RequestBody Resource resource) {
        Resource savedResource = resourceService.addResource(resource);
        return ResponseEntity.ok(savedResource);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<ResourceDTO>> getAllResources() {
        List<ResourceDTO> resources = resourceService.getAllResourceDTOs();
        System.out.println("Current is changing");
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Page<Resource>> getAllResourcesWithPagination(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "availability", required = false) String availability,
            @RequestParam(name = "skillLevel", required = false) String skillLevel,
            @RequestParam(name = "equipmentLevel", required = false) String equipmentLevel,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortDirection", required = false) String sortDirection) {

        Page<Resource> resourcePage = resourceService.getAllResourcesWithPagination(
                page, size, type, availability, skillLevel, equipmentLevel, sortBy, sortDirection);
        return ResponseEntity.ok(resourcePage);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Resource> getResourceById(@PathVariable("id") String resourceId) {
        Resource resource = resourceService.getResourceById(resourceId);
        return ResponseEntity.ok(resource);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Resource> updateResource(@PathVariable("id") String resourceId,
                                                   @RequestBody Resource resource) {
        Resource updatedResource = resourceService.updateResource(resourceId, resource);
        return ResponseEntity.ok(updatedResource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<String> deleteResource(@PathVariable("id") String resourceId) {
        resourceService.deleteResource(resourceId);
        return ResponseEntity.ok("Resource deleted successfully.");
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<Resource>> getResourcesByType(@PathVariable("type") String type) {
        List<Resource> resources = resourceService.getResourcesByType(type);
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<List<Resource>> getAvailableResources() {
        List<Resource> resources = resourceService.getAvailableResources();
        return ResponseEntity.ok(resources);
    }
}
