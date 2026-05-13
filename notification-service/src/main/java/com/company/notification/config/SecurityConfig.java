package com.company.notification.config;

import com.company.notification.config.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.Map;

/**
 * Stateless JWT-based security.
 *
 * - /actuator/health is open (k8s probes).
 * - Everything under /notifications/** requires a valid JWT.
 * - 401 + JSON body returned for unauthenticated requests
 *   so Feign producers get a clear signal.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/api-docs/**").permitAll()
                        // POST /notifications is a fire-and-forget service-to-service call.
                        // Other services (PM, SiteOps) call this without a user JWT.
                        // Reads (GET) still require a valid JWT for RBAC filtering.
                        .requestMatchers(HttpMethod.POST, "/notifications", "/notifications/")
                            .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, resp, e) -> writeError(
                                resp, HttpServletResponse.SC_UNAUTHORIZED,
                                "Authentication required"))
                        .accessDeniedHandler((req, resp, e) -> writeError(
                                resp, HttpServletResponse.SC_FORBIDDEN,
                                "Access denied")))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse resp, int status, String msg)
            throws java.io.IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(resp.getWriter(), Map.of(
                "success", false,
                "message", msg,
                "timestamp", Instant.now().toString()));
    }
}
