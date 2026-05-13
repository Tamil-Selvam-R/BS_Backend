package com.buildsmart.safety.client;

import com.buildsmart.safety.client.dto.PmInternalNotificationRequest;
import com.buildsmart.safety.client.dto.PmNotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * Calls the project-service to fetch TASK_ASSIGNED notifications
 * for the logged-in safety officer, and to push safety notifications
 * into the PM's notification feed.
 *
 * PM SecurityConfig allows /notifications/** for all authenticated roles,
 * so the safety officer's JWT is accepted.
 */
@FeignClient(name = "project-service", contextId = "pmNotificationClient",
        fallback = PmNotificationClientFallback.class)
public interface PmNotificationClient {

    /**
     * GET /api/notifications/to/{userId}
     * Returns all notifications addressed to this userId from the PM service.
     */
    @GetMapping("/api/notifications/to/{userId}")
    List<PmNotificationDto> getNotificationsTo(
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken);

    /**
     * POST /api/notifications/internal
     * Inserts a notification directly into the PM service's database
     * so the recipient sees it in their own PM notification feed.
     */
    @PostMapping("/api/notifications/internal")
    void createInternal(
            @RequestBody PmInternalNotificationRequest request,
            @RequestHeader("Authorization") String bearerToken);
}
