package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.PmNotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Calls the project-service to fetch TASK_ASSIGNED notifications
 * for the logged-in vendor.
 *
 * Targets the same Eureka service ("project-service") as ProjectManagerClient,
 * but uses a distinct contextId so Spring registers two separate Feign beans.
 *
 * The Authorization header is forwarded automatically by
 * com.buildsmart.vendor.config.FeignConfig#requestInterceptor().
 */
@FeignClient(
        name = "project-service",
        contextId = "vendorPmNotificationClient",
        fallbackFactory = PmNotificationClientFallbackFactory.class
)
public interface PmNotificationClient {

    @GetMapping("/api/notifications/to/{userId}")
    List<PmNotificationDto> getNotificationsTo(@PathVariable("userId") String userId);
}
