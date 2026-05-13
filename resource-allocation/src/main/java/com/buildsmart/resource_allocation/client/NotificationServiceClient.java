package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.NotificationCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "notification-service",
        url = "${notification.service.url}",
        path = "/api/notifications",
        contextId = "raNotificationClient",
        fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    @PostMapping
    void create(
            @RequestBody NotificationCreateRequest request,
            @RequestHeader("Authorization") String authorization);
}
