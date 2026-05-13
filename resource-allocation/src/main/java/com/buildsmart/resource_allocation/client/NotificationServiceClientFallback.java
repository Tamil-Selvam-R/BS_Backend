package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.NotificationCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClientFallback.class);

    @Override
    public void create(NotificationCreateRequest request, String authorization) {
        log.warn("[Fallback][NotificationService] create(eventType={}, toRole={}) - Notification service is unavailable, event dropped.",
                request != null ? request.eventType() : "null",
                request != null ? request.toRole() : "null");
    }
}
