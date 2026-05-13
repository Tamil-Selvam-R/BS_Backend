package com.buildsmart.siteops.client.fallback;

import com.buildsmart.siteops.client.ResourceAllocationClient;
import com.buildsmart.siteops.client.dto.AllocationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ResourceAllocationClientFallbackFactory implements FallbackFactory<ResourceAllocationClient> {

    @Override
    public ResourceAllocationClient create(Throwable cause) {
        log.warn("Resource & Allocation service is unavailable — using fallback. Reason: {}", cause.getMessage());
        return new ResourceAllocationClient() {

            @Override
            public List<AllocationDto> getAllocationsByProject(String projectId, String authorization) {
                log.warn("R&A fallback: getAllocationsByProject({}) — resource-allocation unreachable.", projectId);
                return Collections.emptyList();
            }
        };
    }
}
