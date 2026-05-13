package com.company.notification.event;

import com.company.notification.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default no-op publisher. Replaced automatically when a Kafka or WebSocket
 * bean of type {@link NotificationEventPublisher} is registered.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(NotificationEventPublisher.class)
public class NoopNotificationEventPublisher implements NotificationEventPublisher {

    @Override
    public void published(Notification persisted) {
        log.trace("Notification {} persisted (no event sink configured)",
                persisted.getId());
    }
}
