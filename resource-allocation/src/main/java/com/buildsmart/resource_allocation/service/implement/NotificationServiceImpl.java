package com.buildsmart.resource_allocation.service.implement;

import com.buildsmart.resource_allocation.client.NotificationServiceClient;
import com.buildsmart.resource_allocation.client.dto.NotificationCreateRequest;
import com.buildsmart.resource_allocation.dto.NotificationDTO;
import com.buildsmart.resource_allocation.exception.BadRequestException;
import com.buildsmart.resource_allocation.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Pass-through implementation of NotificationService.
 *
 * No local persistence — every call forwards to the central notification-service
 * via Feign. The Feign client is marked optional (required = false) so RA still
 * starts in unit-test environments where the bean isn't registered.
 *
 * Validation: only the bare minimum (toUserId must be present). Anything more
 * strict belongs on the DTO via Bean Validation annotations.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /**
     * Central notification-service Feign client. Optional so the service still
     * boots without it (e.g. when running RA standalone for integration tests).
     */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    @Override
    public void sendNotification(NotificationDTO notificationDTO, String authorizationHeader) {
        if (notificationDTO == null) {
            throw new BadRequestException("Notification payload is required.");
        }
        if (notificationDTO.getToUserId() == null || notificationDTO.getToUserId().isBlank()) {
            throw new BadRequestException(
                    "toUserId is required — every notification must target a specific user.");
        }
        if (notificationDTO.getToRole() == null || notificationDTO.getToRole().isBlank()) {
            throw new BadRequestException("toRole is required.");
        }
        if (notificationDTO.getEventType() == null || notificationDTO.getEventType().isBlank()) {
            throw new BadRequestException("eventType is required.");
        }
        if (notificationDTO.getMessage() == null || notificationDTO.getMessage().isBlank()) {
            throw new BadRequestException("message is required.");
        }

        if (notificationServiceClient == null) {
            log.warn("notification-service client unavailable; dropping notification (event={}, toUserId={})",
                    notificationDTO.getEventType(), notificationDTO.getToUserId());
            return;
        }

        try {
            notificationServiceClient.create(
                    new NotificationCreateRequest(
                            notificationDTO.getEventType(),
                            notificationDTO.getMessage(),
                            "resource-allocation-service",
                            notificationDTO.getFromRole(),
                            notificationDTO.getFromUserId(),
                            notificationDTO.getToRole(),
                            notificationDTO.getToUserId(),
                            notificationDTO.getReferenceId(),
                            notificationDTO.getPayload()
                    ),
                    authorizationHeader);
            log.info("Forwarded notification to central (event={}, toUserId={}, ref={})",
                    notificationDTO.getEventType(),
                    notificationDTO.getToUserId(),
                    notificationDTO.getReferenceId());
        } catch (Exception ex) {
            // Fire-and-forget — never propagate.
            log.warn("notification-service forward failed (event={}, toUserId={}, ref={}): {}",
                    notificationDTO.getEventType(),
                    notificationDTO.getToUserId(),
                    notificationDTO.getReferenceId(),
                    ex.getMessage());
        }
    }
}