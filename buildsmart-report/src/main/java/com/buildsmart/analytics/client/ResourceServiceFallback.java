package com.buildsmart.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ResourceServiceFallback implements ResourceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceFallback.class);

    @Override
    public List<ResourceItemDTO> getAllResources() {
        log.warn("[Fallback][ResourceService] getAllResources() — downstream unavailable, returning empty list");
        return List.of();
    }

    @Override
    public List<ResourceAllocationDTO> getAllAllocations() {
        log.warn("[Fallback][ResourceService] getAllAllocations() — downstream unavailable, returning empty list");
        return List.of();
    }
}
