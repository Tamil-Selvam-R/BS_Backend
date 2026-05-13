package com.buildsmart.siteops.client;

import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import com.buildsmart.siteops.client.fallback.NotificationServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for the Notification service.
 * Eureka service name: "notification-service". Notification service has
 * server.servlet.context-path=/api, so the path includes that prefix.
 *
 * Fire-and-forget: return type is void; the producer ignores the response.
 */
@FeignClient(
        name = "notification-service",
        path = "/api/notifications",
        contextId = "siteOpsNotificationClient",
        fallbackFactory = NotificationServiceClientFallbackFactory.class
)
public interface NotificationServiceClient {

    @PostMapping
    void create(
            @RequestBody NotificationCreateRequest request,
            @RequestHeader("Authorization") String authorization);
}
