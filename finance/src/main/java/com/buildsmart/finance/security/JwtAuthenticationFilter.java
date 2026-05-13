package com.buildsmart.finance.security;

import com.buildsmart.finance.client.UserClient;
import feign.FeignException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String FINANCE_OFFICER_ROLE = "FINANCE_OFFICER";
    
    private final UserClient iamServiceClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || uri.contains("/swagger-ui")
                || uri.contains("/api-docs")
                || uri.contains("/v3/api-docs")
                || uri.endsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid authorization header");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid authorization header");
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // Call IAM service to get user profile with the authorization token
                UserClient.IamProfileResponse profileResponse = iamServiceClient.getCurrentUserProfile(authHeader);

                if (profileResponse == null || profileResponse.data() == null || profileResponse.data().role() == null) {
                    log.warn("Invalid profile response from IAM service");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token or user profile");
                    return;
                }

                String role = profileResponse.data().role().toUpperCase(Locale.ROOT);
                
                // Only allow ADMIN and FINANCE_OFFICER roles
                if (!ADMIN_ROLE.equals(role) && !FINANCE_OFFICER_ROLE.equals(role)) {
                    log.warn("Access denied for role: {}", role);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied. Only ADMIN and FINANCE_OFFICER can access this service. Your role: " + role);
                    return;
                }

                String principal = profileResponse.data().userId() != null
                        ? profileResponse.data().userId()
                        : profileResponse.data().email();

                log.info("User {} with role {} authenticated successfully", principal, role);
                
                // Store the token (without "Bearer " prefix) in credentials so Feign can use it
                String token = authHeader.substring(BEARER_PREFIX.length());
                
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        token,  // Store token without prefix for Feign
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (FeignException.Unauthorized e) {
            log.error("Unauthorized access attempt: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        } catch (FeignException.Forbidden e) {
            log.error("Forbidden access attempt: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access forbidden");
            return;
        } catch (FeignException e) {
            log.error("IAM service communication error: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication service unavailable");
            return;
        } catch (Exception e) {
            log.error("Authentication failed: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
