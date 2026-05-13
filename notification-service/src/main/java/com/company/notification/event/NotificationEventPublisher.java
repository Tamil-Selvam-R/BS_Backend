package com.company.notification.event;

import com.company.notification.model.Notification;

/**
 * Abstraction over "something interesting happened, tell other systems".
 *
 * Today the only path is the synchronous Feign POST that lands in this
 * service and then persists. Tomorrow we may want to ALSO:
 *   - publish to Kafka (notification-events topic)
 *   - push to a WebSocket / SSE channel for the recipient
 *   - dispatch email/SMS via a sidecar
 *
 * Keeping this as an interface means the controller and service stay
 * untouched — we add a Kafka/WebSocket bean and Spring wires it in.
 */
public interface NotificationEventPublisher {
    void published(Notification persisted);
}
