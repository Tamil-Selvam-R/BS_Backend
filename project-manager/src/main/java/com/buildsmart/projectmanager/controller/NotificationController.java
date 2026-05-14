package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.dto.InternalNotificationRequest;
import com.buildsmart.projectmanager.dto.NotificationResponse;
import com.buildsmart.projectmanager.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Notification REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - PROJECT_MANAGER: sees only their own notifications (userId = auth principal from JWT).
 *   - ADMIN: unrestricted access to all notifications.
 *
 * PM JWT filter resolves userId via IAM Feign call and sets it as the Security principal,
 * so {@code auth.getName()} reliably returns the caller's userId.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get all notifications",
               description = "**PROJECT_MANAGER**: returns only the calling user's own notifications.\n\n" +
                       "**ADMIN**: returns all notifications.")
    public ResponseEntity<List<NotificationResponse>> getAllNotifications() {
        if (isAdmin()) {
            return ResponseEntity.ok(notificationService.getAllNotifications());
        }
        String currentUserId = resolveCurrentUserId();
        return ResponseEntity.ok(notificationService.getUserNotifications(currentUserId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get notifications by user ID",
               description = "**PROJECT_MANAGER**: returns 403 if the userId does not match the caller.\n\n" +
                       "**ADMIN**: unrestricted.")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByUser(@PathVariable String userId) {
        if (!isAdmin()) {
            assertUserIdMatchesCaller(userId);
        }
        return ResponseEntity.ok(notificationService.getUserNotifications(userId));
    }

    @GetMapping("/unread-count/{userId}")
    @Operation(summary = "Get unread notification count for user",
               description = "**PROJECT_MANAGER**: returns 403 if the userId does not match the caller.\n\n" +
                       "**ADMIN**: unrestricted.")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable String userId) {
        if (!isAdmin()) {
            assertUserIdMatchesCaller(userId);
        }
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    @PatchMapping("/mark-all-read/{userId}")
    @Operation(summary = "Mark all notifications as read for user",
               description = "**PROJECT_MANAGER**: returns 403 if the userId does not match the caller.\n\n" +
                       "**ADMIN**: unrestricted.")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@PathVariable String userId) {
        if (!isAdmin()) {
            assertUserIdMatchesCaller(userId);
        }
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("markedCount", count));
    }

    @GetMapping("/me")
    @Operation(summary = "Get notifications for current user")
    public ResponseEntity<List<NotificationResponse>> getNotificationsForCurrentUser() {
        String userId = resolveCurrentUserId();
        return ResponseEntity.ok(notificationService.getNotificationsForCurrentUser(userId));
    }

    @GetMapping("/from/{userId}")
    @Operation(summary = "Get notifications sent by a user",
               description = "**PROJECT_MANAGER**: returns 403 if the userId does not match the caller.\n\n" +
                       "**ADMIN**: unrestricted.")
    public ResponseEntity<List<NotificationResponse>> getNotificationsFrom(@PathVariable String userId) {
        if (!isAdmin()) {
            assertUserIdMatchesCaller(userId);
        }
        return ResponseEntity.ok(notificationService.getNotificationsByFrom(userId));
    }

    @GetMapping("/to/{userId}")
    @Operation(summary = "Get notifications sent to a user",
               description = "**PROJECT_MANAGER**: returns 403 if the userId does not match the caller.\n\n" +
                       "**ADMIN**: unrestricted.")
    public ResponseEntity<List<NotificationResponse>> getNotificationsTo(@PathVariable String userId) {
        if (!isAdmin()) {
            assertUserIdMatchesCaller(userId);
        }
        return ResponseEntity.ok(notificationService.getNotificationsByTo(userId));
    }

    @GetMapping("/search")
    @Operation(summary = "Search notifications by sender or recipient",
               description = "**PROJECT_MANAGER**: automatically scoped to the caller's own userId — " +
                       "the notificationFrom/notificationTo params are ignored and replaced by the caller's userId.\n\n" +
                       "**ADMIN**: searches using the provided params.")
    public ResponseEntity<List<NotificationResponse>> searchNotifications(
            @RequestParam(required = false) String notificationFrom,
            @RequestParam(required = false) String notificationTo) {

        // Non-admin: ignore supplied params and scope entirely to caller's identity
        if (!isAdmin()) {
            String currentUserId = resolveCurrentUserId();
            return ResponseEntity.ok(notificationService.getUserNotifications(currentUserId));
        }

        if (notificationFrom != null && !notificationFrom.isBlank()) {
            return ResponseEntity.ok(notificationService.getNotificationsByFrom(notificationFrom));
        }
        if (notificationTo != null && !notificationTo.isBlank()) {
            return ResponseEntity.ok(notificationService.getNotificationsByTo(notificationTo));
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/internal")
    @Operation(summary = "[Internal] Push a notification from another service into this service's DB")
    public ResponseEntity<NotificationResponse> createInternal(
            @RequestBody InternalNotificationRequest request) {
        return ResponseEntity.ok(notificationService.createInternalNotification(request));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the current user's userId from the Security context (set by the JWT filter via IAM). */
    private String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "";
    }

    /** True when the current JWT holder has the ADMIN role. */
    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Throws 403 if the given userId does not match the authenticated caller's userId.
     * Prevents non-admin users from reading another user's notifications.
     */
    private void assertUserIdMatchesCaller(String userId) {
        String currentUserId = resolveCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you can only access your own notifications.");
        }
    }
}
