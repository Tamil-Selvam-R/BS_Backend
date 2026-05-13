package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.dto.InternalNotificationRequest;
import com.buildsmart.projectmanager.dto.NotificationResponse;
import com.buildsmart.projectmanager.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get all notifications")
    public ResponseEntity<List<NotificationResponse>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get notifications by user ID")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId));
    }

    @GetMapping("/unread-count/{userId}")
    @Operation(summary = "Get unread notification count for user")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable String userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    @PatchMapping("/mark-all-read/{userId}")
    @Operation(summary = "Mark all notifications as read for user")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@PathVariable String userId) {
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
    @Operation(summary = "Get notifications sent by a user")
    public ResponseEntity<List<NotificationResponse>> getNotificationsFrom(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getNotificationsByFrom(userId));
    }

    @GetMapping("/to/{userId}")
    @Operation(summary = "Get notifications sent to a user")
    public ResponseEntity<List<NotificationResponse>> getNotificationsTo(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getNotificationsByTo(userId));
    }

    @GetMapping("/search")
    @Operation(summary = "Search notifications by sender or recipient")
    public ResponseEntity<List<NotificationResponse>> searchNotifications(
            @RequestParam(required = false) String notificationFrom,
            @RequestParam(required = false) String notificationTo) {
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

    private String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "";
    }
}
