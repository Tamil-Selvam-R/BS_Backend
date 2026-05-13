package com.buildsmart.siteops.client.fallback;

import com.buildsmart.siteops.client.NotificationServiceClient;
import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationServiceClientFallbackFactory implements FallbackFactory<NotificationServiceClient> {

    @Override
    public NotificationServiceClient create(Throwable cause) {
        log.warn("Notification service is unavailable — using fallback. Reason: {}", cause.getMessage());
        return new NotificationServiceClient() {

            @Override
            public void create(NotificationCreateRequest request, String authorization) {
                log.warn("Notification fallback: create(eventType={}, toRole={}) — notification-service unreachable, event dropped.",
                        request != null ? request.eventType() : "null",
                        request != null ? request.toRole() : "null");
            }
        };
    }
}
