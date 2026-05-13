package com.buildsmart.finance.client;

import com.buildsmart.finance.client.dto.PmNotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class PmNotificationClientFallback implements PmNotificationClient {

    @Override
    public List<PmNotificationDto> getNotificationsTo(String userId, String authHeader) {
        log.warn("project-service unavailable — could not fetch task notifications for user {}", userId);
        return Collections.emptyList();
    }
}
