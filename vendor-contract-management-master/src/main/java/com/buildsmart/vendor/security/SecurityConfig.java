package com.buildsmart.vendor.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: health, swagger
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Internal service-to-service: PM calls this endpoint to push approval decisions back to vendor
                        // No user JWT is available in this flow, so permit without auth
                        .requestMatchers(HttpMethod.PUT, "/api/vendor-integration/approvals/*/status").permitAll()
                        // Internal service-to-service: PM calls this when a task is assigned to a vendor
                        .requestMatchers(HttpMethod.POST, "/api/vendor-integration/tasks/notify").permitAll()
                        // FEATURE SET 2 — SiteOps calls back here to confirm delivery receipt
                        .requestMatchers(HttpMethod.PATCH, "/api/vendor-integration/deliveries/*/site-status").permitAll()
                        // Manual pull-from-site fallback (vendor admin / service-to-service)
                        .requestMatchers(HttpMethod.POST, "/api/vendor-integration/deliveries/*/sync-from-site").permitAll()
                        // Service-to-service: PM → Vendor approval-result callback
                        .requestMatchers("/api/vendor/tasks/internal/**").permitAll()
                        // Read-only vendor list endpoints derived from contracts — consumed by
                        // the analytics/buildsmart-report service over Feign, no user JWT.
                        .requestMatchers(HttpMethod.GET, "/api/vendors", "/api/vendors/**").permitAll()
                        // Allow project manager service to read invoice/document statuses
                        .requestMatchers(HttpMethod.GET, "/api/invoices/status/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/status/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
