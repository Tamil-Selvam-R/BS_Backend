package com.buildsmart.siteops.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.module.name:siteops}")
    private String moduleName;

    @Value("${app.jwt.accepted-modules:siteops,iam}")
    private String acceptedModulesConfig;

    private SecretKey signingKey;
    private Set<String> acceptedModules;

    @PostConstruct
    private void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.acceptedModules = Arrays.stream(acceptedModulesConfig.split(","))
                .map(module -> module == null ? "" : module.trim().toLowerCase(Locale.ROOT))
                .filter(module -> !module.isBlank())
                .collect(Collectors.toCollection(HashSet::new));

        if (acceptedModules.isEmpty()) {
            acceptedModules.add(moduleName.toLowerCase(Locale.ROOT));
        }

        // Accept both module and service-style aliases used by different IAM token issuers.
        String normalizedModule = moduleName == null ? "" : moduleName.trim().toLowerCase(Locale.ROOT);
        if (!normalizedModule.isBlank()) {
            acceptedModules.add(normalizedModule);
            if (normalizedModule.endsWith("-service")) {
                acceptedModules.add(normalizedModule.substring(0, normalizedModule.length() - 8));
            } else {
                acceptedModules.add(normalizedModule + "-service");
            }
        }
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parseToken(token).getSubject();
    }

    public String extractUserId(String token) {
        Claims claims = parseToken(token);
        String userId = claims.get("userId", String.class);
        if (userId == null || userId.isBlank()) {
            throw new JwtException("userId claim not found in JWT token");
        }
        return userId;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = parseToken(token);

        return extractRolesFromClaims(claims);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromClaims(Claims claims) {

        Object role = claims.get("role");
        if (role instanceof String s && !s.isBlank()) {
            return List.of(s);
        }

        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item instanceof String s) {
                            return s;
                        }
                        if (item instanceof java.util.Map<?, ?> map) {
                            Object authority = map.get("authority");
                            if (authority != null) {
                                return authority.toString();
                            }
                        }
                        return item == null ? "" : item.toString();
                    })
                    .toList();
        }

        Object authorities = claims.get("authorities");
        if (authorities instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> {
                        if (item instanceof String s) {
                            return s;
                        }
                        if (item instanceof Map<?, ?> map) {
                            Object authority = map.get("authority");
                            if (authority != null) {
                                return authority.toString();
                            }
                        }
                        return item == null ? "" : item.toString();
                    })
                    .toList();
        }

        if (authorities instanceof String s && !s.isBlank()) {
            return List.of(s);
        }

        return List.of();
    }

    public boolean isValidForModule(String token) {
        try {
            Claims claims = parseToken(token);

            List<String> roles = extractRolesFromClaims(claims).stream()
                    .map(this::normalizeRole)
                    .filter(role -> !role.isBlank())
                    .toList();

            boolean allowedRole = roles.contains("ADMIN") || roles.contains("SITE_ENGINEER") || roles.contains("PROJECT_MANAGER");
            if (!allowedRole) {
                log.warn("JWT token rejected for SiteOps; allowed roles are ADMIN/SITE_ENGINEER/PROJECT_MANAGER. roles={}", roles);
                return false;
            }

            // IAM-issued tokens can be generic and may not include module claim.
            String tokenModule = claims.get("module", String.class);
            if (tokenModule != null && !tokenModule.isBlank()) {
                String normalizedModule = tokenModule.trim().toLowerCase(Locale.ROOT);
                if (!acceptedModules.contains(normalizedModule)) {
                    log.warn("JWT token rejected for SiteOps; module '{}' not in accepted modules {}", tokenModule, acceptedModules);
                    return false;
                }
            }

            Object audienceClaim = claims.get("aud");
            if (audienceClaim instanceof String audience && !audience.isBlank()) {
                String normalizedAudience = audience.trim().toLowerCase(Locale.ROOT);
                if (!acceptedModules.contains(normalizedAudience)) {
                    log.warn("JWT token rejected for SiteOps; aud '{}' not in accepted modules {}", audience, acceptedModules);
                    return false;
                }
            } else if (audienceClaim instanceof Collection<?> audiences && !audiences.isEmpty()) {
                boolean anyAudienceAllowed = audiences.stream()
                        .filter(item -> item != null && !item.toString().isBlank())
                        .map(item -> item.toString().trim().toLowerCase(Locale.ROOT))
                        .anyMatch(acceptedModules::contains);
                if (!anyAudienceAllowed) {
                    log.warn("JWT token rejected for SiteOps; aud list '{}' has no accepted module {}", audiences, acceptedModules);
                    return false;
                }
            }

            String scope = claims.get("scope", String.class);
            if (scope != null && !scope.isBlank()) {
                String normalizedScope = scope.toLowerCase(Locale.ROOT);
                boolean scopeAllowed = acceptedModules.stream().anyMatch(normalizedScope::contains);
                if (!scopeAllowed) {
                    log.warn("JWT token rejected for SiteOps; scope '{}' does not include accepted modules {}", scope, acceptedModules);
                    return false;
                }
            }

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }

        // Handle accidental serialized authority maps, e.g. "{authority=ROLE_ADMIN}".
        if (normalized.contains("ROLE_ADMIN")) {
            return "ADMIN";
        }
        if (normalized.contains("ROLE_SITE_ENGINEER")) {
            return "SITE_ENGINEER";
        }

        return normalized;
    }
}



