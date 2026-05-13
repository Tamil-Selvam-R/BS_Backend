package com.buildsmart.siteops.client.fallback;

import com.buildsmart.siteops.client.PmNotificationClient;
import com.buildsmart.siteops.client.dto.PmNotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class PmNotificationClientFallbackFactory implements FallbackFactory<PmNotificationClient> {

    @Override
    public PmNotificationClient create(Throwable cause) {
        log.warn("project-service unavailable for PM notifications — using fallback. Reason: {}", cause.getMessage());
        return new PmNotificationClient() {

            @Override
            public List<PmNotificationDto> getNotificationsTo(String userId, String bearerToken) {
                log.warn("PM-notification fallback: getNotificationsTo({}) — project-service unreachable.", userId);
                return Collections.emptyList();
            }
        };
    }
}
