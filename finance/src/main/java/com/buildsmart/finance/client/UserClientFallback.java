package com.buildsmart.finance.client;

import com.buildsmart.finance.client.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallback implements UserClient {

    @Override
    public IamProfileResponse getCurrentUserProfile(String bearerToken) {
        log.warn("IAM service is unavailable — circuit breaker fallback triggered");
        // Return null so the service layer's existing fallback (JWT claims) kicks in
        return null;
    }
}
