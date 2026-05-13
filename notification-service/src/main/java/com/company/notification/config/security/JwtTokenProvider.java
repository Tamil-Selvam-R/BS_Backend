package com.company.notification.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Validates JWTs issued by the BuildSmart IAM service.
 *
 * DIAGNOSTIC VERSION — logs every claim it sees at INFO level so we can prove
 * what's actually arriving in the request. Remove the INFO logs after the
 * bug is confirmed fixed.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.issuer:}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 256 bits (32 bytes).");
        }
        this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
        log.info("====== JwtTokenProvider DIAGNOSTIC BUILD loaded ======");
        log.info("JWT verifier initialised. Expected issuer: {}", issuer);
    }

    public AuthenticatedUser parse(String token) {
        try {
            var parserBuilder = Jwts.parser().verifyWith(key);
            if (issuer != null && !issuer.isBlank()) {
                parserBuilder.requireIssuer(issuer);
            }

            Claims claims = parserBuilder.build()
                    .parseSignedClaims(token)
                    .getPayload();

            // ===== DIAGNOSTIC =====
            log.info("====== JWT PARSE START ======");
            log.info("All claims in token: {}", claims);
            log.info("  claims.getSubject()           = '{}'", claims.getSubject());
            log.info("  claims.get(\"userId\", String) = '{}'", claims.get("userId", String.class));
            log.info("  claims.get(\"role\", String)   = '{}'", claims.get("role", String.class));
            log.info("  claims.get(\"name\", String)   = '{}'", claims.get("name", String.class));
            // ===== END DIAGNOSTIC =====

            String userId = claims.get("userId", String.class);
            if (userId == null || userId.isBlank()) {
                userId = claims.getSubject();
                log.info("FALLBACK: userId claim missing; using sub='{}'", userId);
            }

            String role = claims.get("role", String.class);
            Number deptNum = claims.get("departmentId", Number.class);
            Long departmentId = deptNum == null ? null : deptNum.longValue();

            if (role == null || role.isBlank()) {
                throw new JwtException("Missing 'role' claim");
            }
            if (userId == null || userId.isBlank()) {
                throw new JwtException("Missing both 'userId' and 'sub' claims");
            }

            // ===== DIAGNOSTIC =====
            log.info("====== JWT PARSE RESULT ======");
            log.info("  AuthenticatedUser.userId = '{}'  ← THIS is what becomes the filter", userId);
            log.info("  AuthenticatedUser.role   = '{}'", role);
            log.info("====== END ======");
            // ===== END DIAGNOSTIC =====

            return new AuthenticatedUser(userId, role, departmentId);

        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            throw ex;
        }
    }
}