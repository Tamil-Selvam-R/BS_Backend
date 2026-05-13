package com.company.notification.service;

import com.company.notification.dto.NotificationRequest;
import com.company.notification.dto.NotificationResponse;
import com.company.notification.dto.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /** Internal — called by other microservices via Feign. */
    NotificationResponse create(NotificationRequest request);

    /** Bell icon: O(index seek). */
    UnreadCountResponse unreadCountForCurrentUser();

    /** Bell dropdown: paginated, RBAC-filtered. */
    Page<NotificationResponse> listForCurrentUser(String eventType,
                                                  String fromRole,
                                                  Pageable pageable);

    /** PUT /notifications/{id}/read — ownership-checked. */
    NotificationResponse markAsRead(Long id);
}
