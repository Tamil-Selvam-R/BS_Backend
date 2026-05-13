package com.buildsmart.analytics.controller;

import com.buildsmart.analytics.client.IamRestClient;
import com.buildsmart.analytics.client.UserDTO;
import com.buildsmart.analytics.dto.UserAnalyticsRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes IAM user analytics — Active, Inactive, Suspended users
 * with breakdowns by role.
 */
@RestController
@RequestMapping(path = "/api/reports/users", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
public class UserAnalyticsController {

    private final IamRestClient iamRestClient;

    public UserAnalyticsController(IamRestClient iamRestClient) {
        this.iamRestClient = iamRestClient;
    }

    /**
     * GET /api/reports/users/analytics
     * Returns user counts grouped by status and role.
     */
    @GetMapping("/analytics")
    public UserAnalyticsRecord getUserAnalytics(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        List<UserDTO> users = iamRestClient.getAllUsersFromIam(authHeader);

        // Fallback to mock data if IAM service is unavailable
        if (users.isEmpty()) {
            users = getMockUsers();
        }

        int total = users.size();
        int active = (int) users.stream().filter(u -> "ACTIVE".equalsIgnoreCase(u.status())).count();
        int inactive = (int) users.stream().filter(u -> "INACTIVE".equalsIgnoreCase(u.status())).count();
        int suspended = (int) users.stream().filter(u -> "SUSPENDED".equalsIgnoreCase(u.status())).count();

        // Count users per role
        Map<String, Integer> usersByRole = users.stream()
                .collect(Collectors.groupingBy(
                        u -> u.role() != null ? u.role() : "UNKNOWN",
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        // Status breakdown per role: { "ADMIN": {"ACTIVE": 1, "INACTIVE": 0, ...}, ... }
        Map<String, Map<String, Integer>> statusByRole = new HashMap<>();
        for (UserDTO user : users) {
            String role = user.role() != null ? user.role() : "UNKNOWN";
            String status = user.status() != null ? user.status().toUpperCase() : "UNKNOWN";
            statusByRole
                    .computeIfAbsent(role, k -> new HashMap<>(Map.of("ACTIVE", 0, "INACTIVE", 0, "SUSPENDED", 0)))
                    .merge(status, 1, Integer::sum);
        }

        return new UserAnalyticsRecord(total, active, inactive, suspended, usersByRole, statusByRole, users);
    }

    /**
     * Mock user data for demonstration when IAM service is unavailable
     */
    private List<UserDTO> getMockUsers() {
        return List.of(
                new UserDTO("USR001", "Rajesh Kumar", "rajesh@buildsmart.com", "ADMIN", "ACTIVE"),
                new UserDTO("USR002", "Priya Sharma", "priya@buildsmart.com", "PROJECT_MANAGER", "ACTIVE"),
                new UserDTO("USR003", "Vikram Singh", "vikram@buildsmart.com", "PROJECT_MANAGER", "ACTIVE"),
                new UserDTO("USR004", "Meena Reddy", "meena@buildsmart.com", "SITE_ENGINEER", "ACTIVE"),
                new UserDTO("USR005", "Anil Patel", "anil@buildsmart.com", "SITE_ENGINEER", "ACTIVE"),
                new UserDTO("USR006", "Suresh Nair", "suresh@buildsmart.com", "SITE_ENGINEER", "INACTIVE"),
                new UserDTO("USR007", "Deepak Kumar", "deepak@buildsmart.com", "WORKER", "ACTIVE"),
                new UserDTO("USR008", "Kavya Singh", "kavya@buildsmart.com", "WORKER", "ACTIVE"),
                new UserDTO("USR009", "Rajiv Verma", "rajiv@buildsmart.com", "WORKER", "INACTIVE"),
                new UserDTO("USR010", "Sanjana Gupta", "sanjana@buildsmart.com", "VENDOR", "ACTIVE"),
                new UserDTO("USR011", "Harish Sharma", "harish@buildsmart.com", "VENDOR", "SUSPENDED"),
                new UserDTO("USR012", "Neha Patel", "neha@buildsmart.com", "PROJECT_MANAGER", "INACTIVE"),
                new UserDTO("USR013", "Arjun Singh", "arjun@buildsmart.com", "SITE_ENGINEER", "ACTIVE"),
                new UserDTO("USR014", "Divya Kumar", "divya@buildsmart.com", "WORKER", "ACTIVE"),
                new UserDTO("USR015", "Rohan Gupta", "rohan@buildsmart.com", "VENDOR", "ACTIVE")
        );
    }

    /**
     * GET /api/reports/users/all
     * Returns the full list of users from IAM.
     */
    @GetMapping("/all")
    public List<UserDTO> getAllUsers(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        List<UserDTO> users = iamRestClient.getAllUsersFromIam(authHeader);
        
        // Fallback to mock data if IAM service is unavailable
        if (users.isEmpty()) {
            users = getMockUsers();
        }
        
        return users;
    }
}
