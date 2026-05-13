package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.feign.IamServiceClient;
import com.buildsmart.projectmanager.feign.dto.IamAllUsersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "APIs for fetching users from IAM service")
public class UserController {

    @Autowired
    private IamServiceClient iamServiceClient;

    @GetMapping
    @Operation(summary = "Get all users", description = "Fetches all users from the IAM service via Feign client")
    public ResponseEntity<?> getAllUsers(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authorization token is required"));
            }
            IamAllUsersResponse response = iamServiceClient.getAllUsers(authHeader);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching users from IAM service: " + e.getMessage()));
        }
    }
}

