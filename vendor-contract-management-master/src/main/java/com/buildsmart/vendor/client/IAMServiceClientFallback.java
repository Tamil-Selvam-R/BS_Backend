package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.IAMApiResponse;
import com.buildsmart.vendor.client.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for IAMServiceClient.
 * Invoked when the IAM service is unavailable or the circuit is open.
 */
@Component
public class IAMServiceClientFallback implements IAMServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IAMServiceClientFallback.class);

    @Override
    public IAMApiResponse<UserDto> getUserProfile(String authorization) {
        log.warn("IAM Service unavailable – getUserProfile fallback triggered");
        return new IAMApiResponse<>(false, "IAM service is currently unavailable", null);
    }

    @Override
    public IAMApiResponse<Boolean> checkUserRole(String role, String authorization) {
        log.warn("IAM Service unavailable – checkUserRole fallback triggered");
        return new IAMApiResponse<>(false, "IAM service is currently unavailable", false);
    }

    @Override
    public UserDto getUserById(String userId, String authorization) {
        log.warn("IAM Service unavailable – getUserById fallback triggered for userId={}", userId);
        return new UserDto(userId, "Unknown", "unknown@unknown.com", "UNKNOWN", "UNKNOWN");
    }

    @Override
    public UserDto getUserByEmail(String email, String authorization) {
        log.warn("IAM Service unavailable – getUserByEmail fallback triggered for email={}", email);
        return new UserDto(null, "Unknown", email, "UNKNOWN", "UNKNOWN");
    }
}
