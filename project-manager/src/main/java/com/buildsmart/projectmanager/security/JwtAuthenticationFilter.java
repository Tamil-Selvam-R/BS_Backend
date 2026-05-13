package com.buildsmart.projectmanager.security;

import com.buildsmart.projectmanager.feign.IamServiceClient;
import com.buildsmart.projectmanager.feign.dto.IamProfileResponse;
import feign.FeignException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final IamServiceClient iamServiceClient;

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
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                IamProfileResponse profileResponse = iamServiceClient.getCurrentUserProfile(authHeader);

                if (profileResponse == null || profileResponse.data() == null || profileResponse.data().role() == null) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }

                String role = profileResponse.data().role().toUpperCase(Locale.ROOT);
                if (!"ADMIN".equals(role) && !"PROJECT_MANAGER".equals(role)
                        && !"VENDOR".equals(role) && !"SITE_ENGINEER".equals(role)
                        && !"SAFETY_OFFICER".equals(role) && !"FINANCE_OFFICER".equals(role)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied for role: " + role);
                    return;
                }

                String principal = profileResponse.data().userId();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        authHeader.substring(BEARER_PREFIX.length()),
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (FeignException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
