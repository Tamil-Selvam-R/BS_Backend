package com.buildsmart.finance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Calls the IAM service's existing GET /users/profile endpoint.
 * The caller's JWT is forwarded — IAM validates it, queries DB,
 * and returns the live user with fresh name + status.
 * No new endpoints needed in IAM.
 */
@FeignClient(name = "iam-service", fallback = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/users/profile")
    IamProfileResponse getCurrentUserProfile(
            @RequestHeader("Authorization") String bearerToken);

    record IamProfileResponse(boolean success, String message, UserData data) {}
    record UserData(String userId, String name, String email, String role, String status) {}
}
