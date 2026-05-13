package com.buildsmart.resource_allocation.repository;

import com.buildsmart.resource_allocation.entities.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, String> {

    List<Resource> findByType(String type);

    List<Resource> findByAvailability(String availability);

    @Query("SELECT r.resourceId FROM Resource r ORDER BY r.resourceId DESC LIMIT 1")
    String findLastResourceId();

    @Query("SELECT r FROM Resource r WHERE "
            + "(:type IS NULL OR r.type = :type) AND "
            + "(:availability IS NULL OR r.availability = :availability) AND "
            + "(:skillLevel IS NULL OR r.skillLevel = :skillLevel) AND "
            + "(:equipmentLevel IS NULL OR r.equipmentLevel = :equipmentLevel)")
    Page<Resource> findWithFilters(
            @Param("type") String type,
            @Param("availability") String availability,
            @Param("skillLevel") String skillLevel,
            @Param("equipmentLevel") String equipmentLevel,
            Pageable pageable);
}
