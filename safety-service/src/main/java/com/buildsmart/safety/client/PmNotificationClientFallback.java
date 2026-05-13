package com.buildsmart.safety.client;

import com.buildsmart.safety.client.dto.PmInternalNotificationRequest;
import com.buildsmart.safety.client.dto.PmNotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class PmNotificationClientFallback implements PmNotificationClient {

    @Override
    public List<PmNotificationDto> getNotificationsTo(String userId, String bearerToken) {
        log.warn("Project-service unavailable (circuit breaker) — could not fetch " +
                "task notifications for officer {}", userId);
        return Collections.emptyList();
    }

    @Override
    public void createInternal(PmInternalNotificationRequest request, String bearerToken) {
        log.warn("Project-service unavailable (circuit breaker) — could not push " +
                "safety notification to PM feed for user {}", request.notificationTo());
    }
}
