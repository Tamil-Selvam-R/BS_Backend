package com.buildsmart.resource_allocation.client;

import com.buildsmart.resource_allocation.client.dto.IAMApiResponse;
import com.buildsmart.resource_allocation.client.dto.RoleCheckResponseDTO;
import com.buildsmart.resource_allocation.client.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for calling the IAM micro-service.
 *
 * Uses IAM UserController endpoints to get user details.
 * The caller JWT is forwarded via the Authorization header.
 */
@FeignClient(name = "iam-service", url = "${iam.service.url}", fallback = IAMServiceClientFallback.class)
public interface IAMServiceClient {

    /**
     * Fetches the authenticated user profile from the IAM service.
     */
    @GetMapping("/users/profile")
    IAMApiResponse<UserDTO> getUserProfile(
            @RequestHeader("Authorization") String authorization);

    /**
     * Checks whether the authenticated user holds a specific role.
     * IAM returns { success, message, data: { currentRole, hasRequiredRole } }
     */
    @GetMapping("/users/check-role/{role}")
    IAMApiResponse<RoleCheckResponseDTO> checkUserRole(
            @PathVariable("role") String role,
            @RequestHeader("Authorization") String authorization);

    /**
     * Fetches a user by their ID from the IAM service.
     */
    @GetMapping("/users/{userId}")
    UserDTO getUserById(
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    /**
     * Fetches a user by their email from the IAM service.
     */
    @GetMapping("/users/by-email")
    UserDTO getUserByEmail(
            @RequestParam("email") String email,
            @RequestHeader("Authorization") String authorization);
}

