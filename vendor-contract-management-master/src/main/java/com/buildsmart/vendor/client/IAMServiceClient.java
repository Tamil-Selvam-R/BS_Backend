package com.buildsmart.vendor.client;

import com.buildsmart.vendor.client.dto.IAMApiResponse;
import com.buildsmart.vendor.client.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the IAM micro-service.
 * The caller's JWT is forwarded via the Authorization header.
 */
@FeignClient(name = "iam-service", fallback = IAMServiceClientFallback.class)
public interface IAMServiceClient {

    @GetMapping("/users/profile")
    IAMApiResponse<UserDto> getUserProfile(
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/users/check-role/{role}")
    IAMApiResponse<Boolean> checkUserRole(
            @PathVariable("role") String role,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/users/{userId}")
    UserDto getUserById(
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/users/by-email")
    UserDto getUserByEmail(
            @RequestParam("email") String email,
            @RequestHeader("Authorization") String authorization);
}
