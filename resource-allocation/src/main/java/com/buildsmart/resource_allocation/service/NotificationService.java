package com.buildsmart.resource_allocation.service;

import com.buildsmart.resource_allocation.dto.NotificationDTO;

/**
 * Thin pass-through to the central notification-service.
 *
 * RA does not store notifications locally — every request is forwarded over
 * Feign to the platform-wide notification-service. Provided so other RA code
 * (or the REST endpoint) can fire a user-targeted notification without
 * knowing the Feign client internals.
 */
public interface NotificationService {

    /**
     * Forwards the request to the central notification-service. Fire-and-forget:
     * a transport failure is logged but never thrown to the caller.
     *
     * @param notificationDTO     validated payload — toUserId must be present
     * @param authorizationHeader optional Bearer token; pass through if you have
     *                            an active request context, or null for
     *                            service-to-service calls
     */
    void sendNotification(NotificationDTO notificationDTO, String authorizationHeader);
}