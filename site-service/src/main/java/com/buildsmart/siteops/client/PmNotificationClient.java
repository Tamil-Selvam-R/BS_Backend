package com.buildsmart.siteops.client;

import com.buildsmart.siteops.client.dto.PmNotificationDto;
import com.buildsmart.siteops.client.fallback.PmNotificationClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * Calls the project-service to fetch TASK_ASSIGNED notifications
 * for the logged-in site engineer.
 *
 * Targets the same Eureka service ("project-service") as ProjectManagerClient,
 * but uses a distinct contextId so Spring registers two separate Feign beans.
 */
@FeignClient(
        name = "project-service",
        contextId = "siteOpsPmNotificationClient",
        fallbackFactory = PmNotificationClientFallbackFactory.class
)
public interface PmNotificationClient {

    @GetMapping("/api/notifications/to/{userId}")
    List<PmNotificationDto> getNotificationsTo(
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken);
}
