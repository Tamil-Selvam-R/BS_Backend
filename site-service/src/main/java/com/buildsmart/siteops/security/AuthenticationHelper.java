package com.buildsmart.siteops.security;

import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

/**
 * Helper to extract user information from JWT token.
 * Eliminates need for extra IAM service calls.
 */
@Component
public class AuthenticationHelper {

    private final JwtUtil jwtUtil;

    public AuthenticationHelper(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Extract user ID from JWT Authorization header.
     * 
     * @param authorizationHeader "Bearer <token>"
     * @return user ID from JWT
     * @throws IllegalArgumentException if token is invalid or userId not found
     */
    public String extractUserIdFromHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Authorization header is required");
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Authorization header format. Expected 'Bearer <token>'");
        }

        String token = authorizationHeader.substring(7);

        try {
            if (!jwtUtil.isValidForModule(token)) {
                throw new JwtException("JWT token is not valid for this module");
            }
            return jwtUtil.extractUserId(token);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * Extract user ID from raw JWT token.
     * 
     * @param token JWT token (without "Bearer" prefix)
     * @return user ID from JWT
     * @throws JwtException if token is invalid
     */
    public String extractUserId(String token) {
        if (!jwtUtil.isValidForModule(token)) {
            throw new JwtException("JWT token is not valid for this module");
        }
        return jwtUtil.extractUserId(token);
    }
}

