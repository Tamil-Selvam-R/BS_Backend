package com.buildsmart.analytics.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

/**
 * Feign client for the Resource &amp; Allocation microservice.
 * Eureka-registered name: "resource-allocation"
 * (resource-allocation/resource-allocation/src/main/resources/application.properties).
 * The controllers map at /api/resources and /api/allocations with no context-path,
 * so these Feign paths match exactly.
 */
@FeignClient(name = "resource-allocation", fallback = ResourceServiceFallback.class, url = "${resource.service.url}")
public interface ResourceServiceClient {

    @GetMapping("/api/resources")
    List<ResourceItemDTO> getAllResources();

    @GetMapping("/api/allocations")
    List<ResourceAllocationDTO> getAllAllocations();
}
