package com.buildsmart.finance.client;

import com.buildsmart.finance.client.dto.AllocationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResourceAllocationClientFallback implements ResourceAllocationClient {

    @Override
    public AllocationDto getAllocationCost(String allocationId, String authHeader) {
        log.warn("Resource allocation service is unavailable — circuit breaker fallback triggered for allocation {}", allocationId);
        return null;
    }
}
