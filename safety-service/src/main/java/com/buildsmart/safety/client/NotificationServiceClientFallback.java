package com.buildsmart.safety.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for NotificationServiceClient — logs and swallows failures so a
 * down notification-service never blocks the safety officer's primary action.
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void create(NotificationPayload payload) {
        log.warn("notification-service unavailable — dropped event '{}' (ref='{}'). " +
                "The local safety record is still saved.",
                payload != null ? payload.eventType() : "null",
                payload != null ? payload.referenceId() : "null");
    }
}
