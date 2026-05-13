package com.buildsmart.resource_allocation.controller;

import com.buildsmart.resource_allocation.dto.NotificationDTO;
import com.buildsmart.resource_allocation.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint exposing the RA pass-through notification service.
 *
 * Single endpoint:
 *   POST /api/resource-allocation/notifications
 *
 * Sole purpose: let a PM (or admin) trigger a user-targeted notification from
 * inside RA without depending on the central service URL or schema. The body
 * is validated and forwarded to the platform-wide notification-service.
 *
 * Note: this controller does NOT replicate the legacy CRUD endpoints
 * (list/get/mark-read/delete). Recipients use the central service's
 * GET /api/notifications endpoints for that — there's only one bell icon.
 */
@RestController
@RequestMapping("/api/resource-allocation/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Forward a notification to the central service. Returns 202 Accepted with
     * a short status payload — there is no local row to echo back.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PROJECT_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> sendNotification(
            @Valid @RequestBody NotificationDTO notificationDTO,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        notificationService.sendNotification(notificationDTO, authorizationHeader);

        return ResponseEntity.accepted().body(Map.of(
                "status", "forwarded",
                "toUserId", notificationDTO.getToUserId(),
                "eventType", notificationDTO.getEventType()
        ));
    }
}