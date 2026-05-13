package com.buildsmart.finance.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for NotificationServiceClient — logs and swallows failures so a
 * down notification-service never blocks Finance approval transactions.
 *
 * Logs the recipient userId so a dropped notification can be replayed
 * (or manually reissued) without grepping the call site.
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void create(NotificationPayload payload) {
        log.warn("notification-service unavailable — dropped event '{}' for user '{}' (ref '{}').",
                payload != null ? payload.eventType()  : "null",
                payload != null ? payload.toUserId()   : "null",
                payload != null ? payload.referenceId(): "null");
    }
}