package com.buildsmart.siteops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserValidationService userValidationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserValidationService userValidationService) {
        this.jwtUtil = jwtUtil;
        this.userValidationService = userValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            // Step 1: Validate JWT signature and module claim
            if (!jwtUtil.isValidForModule(token)) {
                log.warn("JWT token rejected - not valid for this module or structurally invalid");
                filterChain.doFilter(request, response);
                return;
            }

            try {
                String email   = jwtUtil.extractEmail(token);
                String userId  = jwtUtil.extractUserId(token);
                List<String> roles = jwtUtil.extractRoles(token);

                // Step 2: Verify user still exists and is ACTIVE in IAM.
                //
                // Problem this solves:
                //   A deleted user's JWT is still cryptographically valid for up to 24 hours.
                //   Without this check, deleted users can keep accessing siteops endpoints.
                //
                // How it works:
                //   We call IAM's /users/profile with the user's own token. IAM will:
                //   - return 404 if the user row was deleted
                //   - return status != ACTIVE if the account was suspended
                //   The result is cached in-memory for 5 minutes (configurable) to avoid
                //   hitting IAM on every single request.
                //
                // Fail-open:
                //   If IAM is temporarily unreachable, we still allow through (the JWT
                //   signature proves identity) and log a warning. This prevents a brief
                //   IAM outage from taking down siteops entirely.
                if (!userValidationService.isUserActive(userId, token)) {
                    log.warn("Access denied — userId={} is deleted or inactive in IAM", userId);
                    sendUnauthorized(response,
                            "Your account has been deleted or deactivated. Please contact an administrator.");
                    return;
                }

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> role == null ? "" : role.trim())
                        .filter(role -> !role.isBlank())
                        .map(role -> role.toUpperCase(Locale.ROOT))
                        .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .distinct()
                        .toList();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (JwtException | IllegalArgumentException e) {
                log.warn("JWT claim extraction failed: {}", e.getMessage());
                // Continue without authentication — will trigger 403 Forbidden
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Writes a clean 401 JSON response back to the client so the frontend
     * gets a proper error object (not a Spring whitepage or empty body).
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "timestamp",  LocalDateTime.now().toString(),
                "statusCode", 401,
                "message",    "Unauthorized",
                "details",    message
        );

        objectMapper.writeValue(response.getWriter(), body);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

