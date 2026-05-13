package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationServiceClientFallbackFactory implements FallbackFactory<NotificationServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClientFallbackFactory.class);

    @Override
    public NotificationServiceClient create(Throwable cause) {
        return new NotificationServiceClient() {

            @Override
            public void create(NotificationCreateRequest request) {
                log.warn("Notification fallback: create(eventType={}, toRole={}) — notification-service unreachable, event dropped. Cause: {}",
                        request != null ? request.eventType() : "null",
                        request != null ? request.toRole() : "null",
                        cause.getMessage());
            }
        };
    }
}
