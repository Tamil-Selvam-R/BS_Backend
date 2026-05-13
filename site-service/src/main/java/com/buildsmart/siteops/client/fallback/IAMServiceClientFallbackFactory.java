package com.buildsmart.siteops.client.fallback;

import com.buildsmart.siteops.client.IAMServiceClient;
import com.buildsmart.siteops.client.dto.IAMApiResponse;
import com.buildsmart.siteops.client.dto.UserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Resilience4j fallback factory for IAMServiceClient.
 *
 * When the IAM service is down or slow, these fallback methods are called
 * instead of throwing an exception — keeping SiteOps operational.
 */
@Slf4j
@Component
public class IAMServiceClientFallbackFactory implements FallbackFactory<IAMServiceClient> {

    @Override
    public IAMServiceClient create(Throwable cause) {
        log.warn("IAM service is unavailable — using fallback. Reason: {}", cause.getMessage());
        return new IAMServiceClient() {

            @Override
            public IAMApiResponse<UserDto> getUserProfile(String authorization) {
                log.warn("IAM fallback: getUserProfile — IAM unreachable.");
                return new IAMApiResponse<>(false, "IAM service is currently unavailable.", null);
            }

            @Override
            public IAMApiResponse<Boolean> checkUserRole(String role, String authorization) {
                log.warn("IAM fallback: checkUserRole({}) — IAM unreachable.", role);
                return new IAMApiResponse<>(false, "IAM service is currently unavailable.", false);
            }

            @Override
            public UserDto getUserById(String userId, String authorization) {
                log.warn("IAM fallback: getUserById({}) — IAM unreachable.", userId);
                // Return a minimal stub — callers should handle null data gracefully
                return null;
            }

            @Override
            public UserDto getUserByEmail(String email, String authorization) {
                log.warn("IAM fallback: getUserByEmail({}) — IAM unreachable.", email);
                return null;
            }
        };
    }
}

