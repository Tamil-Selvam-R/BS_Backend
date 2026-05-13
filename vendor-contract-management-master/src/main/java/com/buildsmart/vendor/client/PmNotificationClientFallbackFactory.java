package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.PmNotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class PmNotificationClientFallbackFactory implements FallbackFactory<PmNotificationClient> {

    private static final Logger log = LoggerFactory.getLogger(PmNotificationClientFallbackFactory.class);

    @Override
    public PmNotificationClient create(Throwable cause) {
        log.warn("project-service unavailable for PM notifications — using fallback. Reason: {}", cause.getMessage());
        return userId -> {
            log.warn("PM-notification fallback: getNotificationsTo({}) — project-service unreachable.", userId);
            return Collections.<PmNotificationDto>emptyList();
        };
    }
}
