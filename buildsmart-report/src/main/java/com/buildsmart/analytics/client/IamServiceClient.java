package com.buildsmart.analytics.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;


@FeignClient(
        name = "iam-service",
        url = "${iam.service.url}",
        fallback = IamServiceFallback.class
)
public interface IamServiceClient {


    @GetMapping("/admin/users")
    IamApiResponse<List<IamUserDTO>> getAllUsersWrapped();


    @GetMapping("/api/v1/users/profile")
    IamApiResponse<IamUserDTO> getUserProfile();

    default UserDTO getUserById(String userId) {
        // Don't call IAM's broken /api/v1/users/{id} endpoint —
        // it returns 500 and would open the circuit breaker,
        // blocking getAllUsersWrapped() from working.
        return new UserDTO(userId, "Unknown", "unknown@buildsmart.com", "UNKNOWN", "ACTIVE");
    }

    default java.util.List<UserDTO> getAllUsers() {
        try {
            IamApiResponse<java.util.List<IamUserDTO>> response = getAllUsersWrapped();
            if (response != null && response.success() && response.data() != null) {
                return response.data().stream()
                        .map(u -> new UserDTO(u.userId(), u.name(), u.email(), u.role(), u.status()))
                        .toList();
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch users from IAM: " + e.getMessage());
        }
        return java.util.List.of();
    }

    default UserDTO getUserByEmail(String email) {
        return new UserDTO("UNKNOWN", "Unknown", email, "UNKNOWN", "ACTIVE");
    }
}
