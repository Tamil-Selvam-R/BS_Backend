package com.buildsmart.vendor.client.dto;


public record NotificationCreateRequest(
        String eventType,
        String message,
        String fromService,
        String fromRole,
        String fromUserId,
        String toRole,
        String toUserId,
        String referenceId,
        String payload
) {}