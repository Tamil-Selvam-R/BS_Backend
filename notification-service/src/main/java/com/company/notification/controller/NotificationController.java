package com.company.notification.controller;

import com.company.notification.dto.ApiResponse;
import com.company.notification.dto.NotificationRequest;
import com.company.notification.dto.NotificationResponse;
import com.company.notification.dto.UnreadCountResponse;
import com.company.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * THE one and only notification controller.
 *
 * Usage map:
 *   POST   /notifications                – internal, called by other services via Feign
 *   GET    /notifications/unread-count   – bell icon (poll every 15-30s)
 *   GET    /notifications                – bell dropdown (paginated, RBAC-filtered)
 *   PUT    /notifications/{id}/read      – mark as read
 *
 * RBAC is enforced inside the service layer using the JWT-derived
 * AuthenticatedUser — the controller does NOT accept role/dept from the client.
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /* ---------- 1) CREATE (Feign-only in practice) ---------- */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NotificationResponse> create(
            @Valid @RequestBody NotificationRequest request) {
        log.debug("Inbound create: {}", request);
        return ApiResponse.ok(
                notificationService.create(request),
                "Notification created");
    }

    /* ---------- 2) UNREAD COUNT (bell icon) ---------- */

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCountForCurrentUser());
    }

    /* ---------- 3) LIST (bell dropdown) ---------- */

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String fromRole,
            @PageableDefault(size = 20, sort = "createdAt",
                             direction = Sort.Direction.DESC) Pageable pageable) {

        // Cap page size to prevent DoS via huge page requests
        int safeSize = Math.min(pageable.getPageSize(), 100);
        Pageable safe = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), safeSize, pageable.getSort());

        return ApiResponse.ok(notificationService
                .listForCurrentUser(eventType, fromRole, safe));
    }

    /* ---------- 4) MARK AS READ ---------- */

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.ok(notificationService.markAsRead(id),
                        "Notification marked as read"));
    }
}
