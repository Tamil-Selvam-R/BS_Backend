package com.buildsmart.finance.client;

import com.buildsmart.finance.client.dto.AllocationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "resource-allocation", fallback = ResourceAllocationClientFallback.class)
public interface ResourceAllocationClient {

    @GetMapping("/api/allocations/{allocationId}/cost")
    AllocationDto getAllocationCost(
            @PathVariable("allocationId") String allocationId,
            @RequestHeader("Authorization") String authHeader);
}
