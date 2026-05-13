package com.buildsmart.resource_allocation.service.implement;

import com.buildsmart.resource_allocation.entities.Resource;
import com.buildsmart.resource_allocation.entities.Allocation;
import com.buildsmart.resource_allocation.dto.ResourceDTO;
import com.buildsmart.resource_allocation.repository.ResourceRepository;
import com.buildsmart.resource_allocation.repository.AllocationRepository;
import com.buildsmart.resource_allocation.exception.ResourceNotFoundException;
import com.buildsmart.resource_allocation.exception.BadRequestException;
import com.buildsmart.resource_allocation.exception.ResourceAlreadyAllocatedException;
import com.buildsmart.resource_allocation.utility.IdGeneratorUtil;
import com.buildsmart.resource_allocation.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ResourceServiceImpl implements ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceImpl.class);

    private final ResourceRepository resourceRepository;
    private final AllocationRepository allocationRepository;

    public ResourceServiceImpl(ResourceRepository resourceRepository,
                               AllocationRepository allocationRepository) {
        this.resourceRepository = resourceRepository;
        this.allocationRepository = allocationRepository;
    }

    @Override
    public Resource addResource(Resource resource) {

        if (resource.getType() == null || resource.getType().trim().isEmpty()) {
            throw new BadRequestException("Resource type is required. Allowed values: 'Labor' or 'Equipment'.");
        }

        String type = resource.getType().trim();
        if (!type.equals("Labor") && !type.equals("Equipment")) {
            throw new BadRequestException("Resource type must be 'Labor' or 'Equipment'.");
        }

        resource.setType(type);

        if (resource.getAvailability() == null || resource.getAvailability().trim().isEmpty()) {
            resource.setAvailability("Available");
        } else {
            String availability = resource.getAvailability().trim();
            if (!availability.equals("Available")
                    && !availability.equals("Unavailable")
                    && !availability.equals("On Leave")) {
                throw new BadRequestException("Availability must be 'Available', 'Unavailable', or 'On Leave'.");
            }

            if (type.equals("Equipment") && availability.equals("On Leave")) {
                throw new BadRequestException("Equipment cannot have 'On Leave' status. Only Labor can be 'On Leave'.");
            }

            resource.setAvailability(availability);
        }

        if (resource.getResourceId() != null && !resource.getResourceId().trim().isEmpty()) {
            throw new BadRequestException("Resource ID is auto-generated. You must not provide a resourceId in the request body.");
        }

        resource.setCostPerHour(null);
        resource.setTotalCost(null);

        validateLaborFields(resource, type);
        validateEquipmentFields(resource, type);
        setCostPerHourBasedOnType(resource, type);

        String lastId = resourceRepository.findLastResourceId();
        String newId = IdGeneratorUtil.nextResourceId(lastId);
        resource.setResourceId(newId);

        return resourceRepository.save(resource);
    }


    @Override
    public List<Resource> getAllResources() {
        return resourceRepository.findAll();
    }

    @Override
    public List<ResourceDTO> getAllResourceDTOs() {
        List<Resource> resources = resourceRepository.findAll();
        List<ResourceDTO> dtoList = new java.util.ArrayList<>();
        for (Resource resource : resources) {
            ResourceDTO dto = new ResourceDTO();
            dto.setResourceId(resource.getResourceId());
            dto.setType(resource.getType());
            dto.setAvailability(resource.getAvailability());
            dto.setNumberOfLabors(resource.getNumberOfLabors());
            dto.setSkillLevel(resource.getSkillLevel());
            dto.setEquipmentName(resource.getEquipmentName());
            dto.setEquipmentLevel(resource.getEquipmentLevel());
            dto.setCostPerHour(resource.getCostPerHour());
            dto.setTotalCost(resource.getTotalCost());
            dtoList.add(dto);
        }
        return dtoList;
    }

    private void validateResourceIdFormat(String resourceId) {
        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw new BadRequestException("Resource ID is required.");
        }
        if (!resourceId.trim().matches("RESBS\\d{3}")) {
            throw new BadRequestException("Invalid Resource ID format. Resource ID must follow the format: RESBS + 3-digit number (example: RESBS001).");
        }
    }

    @Override
    public Resource getResourceById(String resourceId) {

        validateResourceIdFormat(resourceId);

        Resource foundResource = resourceRepository.findById(resourceId).orElse(null);
        if (foundResource == null) {
            throw new ResourceNotFoundException("Resource not found with ID: " + resourceId);
        }

        return foundResource;
    }

    @Override
    public Resource updateResource(String resourceId, Resource resource) {

        validateResourceIdFormat(resourceId);

        Resource existingResource = resourceRepository.findById(resourceId).orElse(null);
        if (existingResource == null) {
            throw new ResourceNotFoundException("Resource not found with ID: " + resourceId);
        }

        String currentType = existingResource.getType();

        if (resource.getType() != null && !resource.getType().trim().isEmpty()) {
            String type = resource.getType().trim();
            if (!type.equals("Labor") && !type.equals("Equipment")) {
                throw new BadRequestException("Resource type must be 'Labor' or 'Equipment'.");
            }

            List<Allocation> activeAllocations = allocationRepository
                    .findByResource_ResourceIdAndStatus(resourceId, "Active");
            if (!activeAllocations.isEmpty()) {
                throw new BadRequestException("Cannot change type while resource has active allocations.");
            }

            currentType = type;
            existingResource.setType(type);
        }

        if (resource.getAvailability() != null && !resource.getAvailability().trim().isEmpty()) {
            String availability = resource.getAvailability().trim();
            if (!availability.equals("Available")
                    && !availability.equals("Unavailable")
                    && !availability.equals("On Leave")) {
                throw new BadRequestException("Availability must be 'Available', 'Unavailable', or 'On Leave'.");
            }

            if (currentType.equals("Equipment") && availability.equals("On Leave")) {
                throw new BadRequestException("Equipment cannot have 'On Leave' status. Only Labor can be 'On Leave'.");
            }

            List<Allocation> activeAllocations = allocationRepository
                    .findByResource_ResourceIdAndStatus(resourceId, "Active");

            if (availability.equals("On Leave") && !activeAllocations.isEmpty()) {
                throw new BadRequestException("Cannot set 'On Leave' while resource has active allocations. Release them first.");
            }

            existingResource.setAvailability(availability);
        }

        if (currentType.equals("Labor")) {
            if (resource.getNumberOfLabors() != null) {
                if (resource.getNumberOfLabors() <= 0) {
                    throw new BadRequestException("Number of labors must be greater than 0.");
                }
                if (resource.getNumberOfLabors() > 500) {
                    throw new BadRequestException("Number of labors cannot exceed 500.");
                }
                existingResource.setNumberOfLabors(resource.getNumberOfLabors());
            }

            if (resource.getSkillLevel() != null && !resource.getSkillLevel().trim().isEmpty()) {
                String skillLevel = resource.getSkillLevel().trim();
                if (!skillLevel.equals("Skilled") && !skillLevel.equals("Semi-Skilled") && !skillLevel.equals("Unskilled")) {
                    throw new BadRequestException("Skill level must be 'Skilled', 'Semi-Skilled', or 'Unskilled'.");
                }
                existingResource.setSkillLevel(skillLevel);
                Double costPerHour = getCostPerHourForLabor(skillLevel);
                existingResource.setCostPerHour(costPerHour);
            }

            existingResource.setEquipmentName(null);
            existingResource.setEquipmentLevel(null);
        }

        if (currentType.equals("Equipment")) {
            if (resource.getEquipmentName() != null && !resource.getEquipmentName().trim().isEmpty()) {
                String equipmentName = resource.getEquipmentName().trim();
                if (equipmentName.length() < 2) {
                    throw new BadRequestException("Equipment name must be at least 2 characters long.");
                }
                if (equipmentName.length() > 100) {
                    throw new BadRequestException("Equipment name cannot exceed 100 characters.");
                }
                if (!equipmentName.matches("[a-zA-Z0-9 \\-]+")) {
                    throw new BadRequestException("Equipment name can only contain letters, numbers, spaces, and hyphens.");
                }
                existingResource.setEquipmentName(equipmentName);
            }

            if (resource.getEquipmentLevel() != null && !resource.getEquipmentLevel().trim().isEmpty()) {
                String equipmentLevel = resource.getEquipmentLevel().trim();
                if (!equipmentLevel.equals("Heavy") && !equipmentLevel.equals("Medium") && !equipmentLevel.equals("Light")) {
                    throw new BadRequestException("Equipment level must be 'Heavy', 'Medium', or 'Light'.");
                }
                existingResource.setEquipmentLevel(equipmentLevel);
                Double costPerHour = getCostPerHourForEquipment(equipmentLevel);
                existingResource.setCostPerHour(costPerHour);
            }

            existingResource.setNumberOfLabors(null);
            existingResource.setSkillLevel(null);
        }

        return resourceRepository.save(existingResource);
    }

    @Override
    public void deleteResource(String resourceId) {

        validateResourceIdFormat(resourceId);

        boolean exists = resourceRepository.existsById(resourceId);
        if (!exists) {
            throw new ResourceNotFoundException("Resource not found with ID: " + resourceId);
        }

        List<Allocation> activeAllocations = allocationRepository
                .findByResource_ResourceIdAndStatus(resourceId, "Active");

        if (!activeAllocations.isEmpty()) {
            throw new ResourceAlreadyAllocatedException("Cannot delete resource. It has active allocations.");
        }

        List<Allocation> pendingAllocations = allocationRepository
                .findByResource_ResourceIdAndStatus(resourceId, "Pending");

        if (!pendingAllocations.isEmpty()) {
            throw new ResourceAlreadyAllocatedException("Cannot delete resource. It has pending allocations.");
        }

        resourceRepository.deleteById(resourceId);
    }

    @Override
    public List<Resource> getResourcesByType(String type) {

        if (!type.equals("Labor") && !type.equals("Equipment")) {
            throw new BadRequestException("Type must be 'Labor' or 'Equipment'.");
        }

        return resourceRepository.findByType(type);
    }

    @Override
    public List<Resource> getAvailableResources() {
        return resourceRepository.findByAvailability("Available");
    }

    private void validateLaborFields(Resource resource, String type) {
        if (type.equals("Labor")) {
            if (resource.getNumberOfLabors() == null) {
                throw new BadRequestException("Number of labors is required for Labor type.");
            }
            if (resource.getNumberOfLabors() <= 0) {
                throw new BadRequestException("Number of labors must be greater than 0.");
            }
            if (resource.getNumberOfLabors() > 500) {
                throw new BadRequestException("Number of labors cannot exceed 500.");
            }

            if (resource.getSkillLevel() == null || resource.getSkillLevel().trim().isEmpty()) {
                throw new BadRequestException("Skill level is required for Labor type. Allowed values: 'Skilled', 'Semi-Skilled', 'Unskilled'.");
            }
            String skillLevel = resource.getSkillLevel().trim();
            if (!skillLevel.equals("Skilled") && !skillLevel.equals("Semi-Skilled") && !skillLevel.equals("Unskilled")) {
                throw new BadRequestException("Skill level must be 'Skilled', 'Semi-Skilled', or 'Unskilled'.");
            }
            resource.setSkillLevel(skillLevel);

            resource.setEquipmentName(null);
            resource.setEquipmentLevel(null);
        }
    }

    private void validateEquipmentFields(Resource resource, String type) {
        if (type.equals("Equipment")) {
            if (resource.getEquipmentName() == null || resource.getEquipmentName().trim().isEmpty()) {
                throw new BadRequestException("Equipment name is required for Equipment type.");
            }
            String equipmentName = resource.getEquipmentName().trim();
            if (equipmentName.length() < 2) {
                throw new BadRequestException("Equipment name must be at least 2 characters long.");
            }
            if (equipmentName.length() > 100) {
                throw new BadRequestException("Equipment name cannot exceed 100 characters.");
            }
            if (!equipmentName.matches("[a-zA-Z0-9 \\-]+")) {
                throw new BadRequestException("Equipment name can only contain letters, numbers, spaces, and hyphens.");
            }
            resource.setEquipmentName(equipmentName);

            if (resource.getEquipmentLevel() == null || resource.getEquipmentLevel().trim().isEmpty()) {
                throw new BadRequestException("Equipment level is required for Equipment type. Allowed values: 'Heavy', 'Medium', 'Light'.");
            }
            String equipmentLevel = resource.getEquipmentLevel().trim();
            if (!equipmentLevel.equals("Heavy") && !equipmentLevel.equals("Medium") && !equipmentLevel.equals("Light")) {
                throw new BadRequestException("Equipment level must be 'Heavy', 'Medium', or 'Light'.");
            }
            resource.setEquipmentLevel(equipmentLevel);

            resource.setNumberOfLabors(null);
            resource.setSkillLevel(null);
        }
    }

    private void setCostPerHourBasedOnType(Resource resource, String type) {
        if (type.equals("Labor")) {
            String skillLevel = resource.getSkillLevel().trim();
            Double costPerHour = getCostPerHourForLabor(skillLevel);
            resource.setCostPerHour(costPerHour);
        }

        if (type.equals("Equipment")) {
            String equipmentLevel = resource.getEquipmentLevel().trim();
            Double costPerHour = getCostPerHourForEquipment(equipmentLevel);
            resource.setCostPerHour(costPerHour);
        }

        resource.setTotalCost(0.0);
    }

    private Double getCostPerHourForLabor(String skillLevel) {
        if (skillLevel.equals("Unskilled")) {
            return 80.0;
        }
        if (skillLevel.equals("Semi-Skilled")) {
            return 120.0;
        }
        if (skillLevel.equals("Skilled")) {
            return 250.0;
        }
        return 0.0;
    }

    private Double getCostPerHourForEquipment(String equipmentLevel) {
        if (equipmentLevel.equals("Light")) {
            return 500.0;
        }
        if (equipmentLevel.equals("Medium")) {
            return 1500.0;
        }
        if (equipmentLevel.equals("Heavy")) {
            return 5000.0;
        }
        return 0.0;
    }

    @Override
    public Page<Resource> getAllResourcesWithPagination(int page, int size,
                                                        String type, String availability,
                                                        String skillLevel, String equipmentLevel,
                                                        String sortBy, String sortDirection) {

        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative.");
        }

        if (size <= 0) {
            throw new BadRequestException("Page size must be greater than 0.");
        }

        if (size > 100) {
            throw new BadRequestException("Page size cannot exceed 100.");
        }

        if (type != null && !type.trim().isEmpty()) {
            type = type.trim();
            if (!type.equals("Labor") && !type.equals("Equipment")) {
                throw new BadRequestException("Type filter must be 'Labor' or 'Equipment'.");
            }
        } else {
            type = null;
        }

        if (availability != null && !availability.trim().isEmpty()) {
            availability = availability.trim();
            if (!availability.equals("Available") && !availability.equals("Unavailable") && !availability.equals("On Leave")) {
                throw new BadRequestException("Availability filter must be 'Available', 'Unavailable', or 'On Leave'.");
            }
        } else {
            availability = null;
        }

        if (skillLevel != null && !skillLevel.trim().isEmpty()) {
            skillLevel = skillLevel.trim();
            if (!skillLevel.equals("Skilled") && !skillLevel.equals("Semi-Skilled") && !skillLevel.equals("Unskilled")) {
                throw new BadRequestException("Skill level filter must be 'Skilled', 'Semi-Skilled', or 'Unskilled'.");
            }
        } else {
            skillLevel = null;
        }

        if (equipmentLevel != null && !equipmentLevel.trim().isEmpty()) {
            equipmentLevel = equipmentLevel.trim();
            if (!equipmentLevel.equals("Heavy") && !equipmentLevel.equals("Medium") && !equipmentLevel.equals("Light")) {
                throw new BadRequestException("Equipment level filter must be 'Heavy', 'Medium', or 'Light'.");
            }
        } else {
            equipmentLevel = null;
        }

        String[] allowedSortFields = {"resourceId", "type", "availability", "skillLevel",
                "equipmentLevel", "costPerHour", "numberOfLabors"};

        String resolvedSortBy = "resourceId";
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String trimmedSortBy = sortBy.trim();
            boolean isValid = false;
            for (String field : allowedSortFields) {
                if (field.equals(trimmedSortBy)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                throw new BadRequestException("Invalid sortBy value '" + trimmedSortBy + "'. Allowed values: resourceId, type, availability, skillLevel, equipmentLevel, costPerHour, numberOfLabors.");
            }
            resolvedSortBy = trimmedSortBy;
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (sortDirection != null && !sortDirection.trim().isEmpty()) {
            String trimmedDirection = sortDirection.trim().toLowerCase();
            if (trimmedDirection.equals("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!trimmedDirection.equals("asc")) {
                throw new BadRequestException("Invalid sortDirection value '" + sortDirection.trim() + "'. Allowed values: 'asc' or 'desc'.");
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, resolvedSortBy));
        return resourceRepository.findWithFilters(type, availability, skillLevel, equipmentLevel, pageable);
    }
}