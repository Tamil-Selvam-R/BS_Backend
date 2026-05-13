package com.buildsmart.finance.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Utility class to extract claims from JWT tokens
 * Decodes JWT tokens locally without making service calls
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Extract userId from JWT token
     *
     * @param token JWT token (without "Bearer " prefix)
     * @return userId from the token's "userId" claim
     */
    public String extractUserId(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.get("userId", String.class);
        } catch (Exception e) {
            log.error("Error extracting userId from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract email from JWT token
     *
     * @param token JWT token (without "Bearer " prefix)
     * @return email from the token's "sub" claim
     */
    public String extractEmail(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.getSubject(); // "sub" claim contains email
        } catch (Exception e) {
            log.error("Error extracting email from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract role from JWT token
     *
     * @param token JWT token (without "Bearer " prefix)
     * @return role from the token's "role" claim
     */
    public String extractRole(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("Error extracting role from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get all claims from the JWT token
     *
     * @param token JWT token (without "Bearer " prefix)
     * @return Claims object containing all token claims
     */
    private Claims getAllClaimsFromToken(String token) {
        byte[] decodedKey = Base64.getDecoder().decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(decodedKey);
        
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract token from Authorization header
     *
     * @param authHeader Authorization header (e.g., "Bearer eyJhbGc...")
     * @return token without "Bearer " prefix, or null if invalid
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7); // Remove "Bearer " prefix
    }
}
