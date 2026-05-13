package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.ResourceAllocatedNotificationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SiteOpsServiceClientFallback implements SiteOpsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SiteOpsServiceClientFallback.class);

    @Override
    public void notifyResourceAllocated(ResourceAllocatedNotificationDTO payload, String authorization) {
        log.warn("[Fallback][SiteOpsService] notifyResourceAllocated() - SiteOps service is unavailable. Allocation '{}' for project '{}' was not pushed.",
                payload != null ? payload.getAllocationId() : "null",
                payload != null ? payload.getProjectId() : "null");
    }
}
