package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.ResourceAllocatedNotificationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "siteops-service", url = "${siteops.service.url}", fallback = SiteOpsServiceClientFallback.class)
public interface SiteOpsServiceClient {

    @PostMapping("/internal/resource-allocated")
    void notifyResourceAllocated(
            @RequestBody ResourceAllocatedNotificationDTO payload,
            @RequestHeader(value = "Authorization", required = false) String authorization);
}
