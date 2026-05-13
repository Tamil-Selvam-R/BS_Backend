package com.buildsmart.resource_allocation.service;

import com.buildsmart.resource_allocation.entities.Resource;
import com.buildsmart.resource_allocation.dto.ResourceDTO;
import org.springframework.data.domain.Page;
import java.util.List;

public interface ResourceService {

    Resource addResource(Resource resource);

    List<Resource> getAllResources();

    List<ResourceDTO> getAllResourceDTOs();

    Resource getResourceById(String resourceId);

    Resource updateResource(String resourceId, Resource resource);

    void deleteResource(String resourceId);

    List<Resource> getResourcesByType(String type);

    List<Resource> getAvailableResources();

    Page<Resource> getAllResourcesWithPagination(int page, int size,
                                                 String type, String availability,
                                                 String skillLevel, String equipmentLevel,
                                                 String sortBy, String sortDirection);

}
