package com.buildsmart.siteops.client;

import com.buildsmart.siteops.client.dto.AllocationDto;
import com.buildsmart.siteops.client.fallback.ResourceAllocationClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * Feign client for the Resource & Allocation service.
 * Uses Eureka service name "resource-allocation".
 *
 * Auth: R&A's controller endpoints require ADMIN or PROJECT_MANAGER role.
 * The forwarded JWT must carry one of those roles for the call to succeed —
 * a SITE_ENGINEER-only token will receive 403 from R&A.
 */
@FeignClient(name = "resource-allocation", fallbackFactory = ResourceAllocationClientFallbackFactory.class)
public interface ResourceAllocationClient {

    @GetMapping("/api/allocations/project/{projectId}")
    List<AllocationDto> getAllocationsByProject(
            @PathVariable("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);
}
