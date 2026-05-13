package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the Notification service.
 * Eureka service name: "notification-service". The Notification service has
 * server.servlet.context-path=/api, so the path includes that prefix.
 *
 * The Authorization header is forwarded automatically by
 * com.buildsmart.vendor.config.FeignConfig#requestInterceptor().
 *
 * Fire-and-forget: return type is void; the producer ignores the response.
 */
@FeignClient(
        name = "notification-service",
        path = "/api/notifications",
        contextId = "vendorNotificationClient",
        fallbackFactory = NotificationServiceClientFallbackFactory.class
)
public interface NotificationServiceClient {

    @PostMapping
    void create(@RequestBody NotificationCreateRequest request);
}
