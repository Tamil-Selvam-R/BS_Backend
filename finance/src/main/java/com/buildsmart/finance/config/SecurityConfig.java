package com.buildsmart.finance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.buildsmart.finance.security.JwtAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/api-docs/**", "/actuator/health").permitAll()
                        // FEATURE SET 1: synchronous budget availability check called by Resource Allocation
                        // service before creating an allocation. Open for service-to-service use.
                        .requestMatchers("/api/budget/approve").permitAll()
                        // NEW FLOW: Resource-Allocation pushes a budget request when a Resource is created.
                        .requestMatchers("/api/finance/budget/resource-request").permitAll()
                        // NEW FLOW: Resource-Allocation pulls the budget approval status by (projectId, resourceId)
                        // just before creating an allocation. No allocationId is used any more.
                        .requestMatchers(HttpMethod.GET, "/api/finance/budget/status").permitAll()
                        // Service-to-service: PM → Finance approval-result callback
                        .requestMatchers("/api/finance/tasks/internal/**").permitAll()
                        // Service-to-service: PM → Finance budget/expense approval callbacks.
                        // PM's JWT carries PROJECT_MANAGER role which Finance would reject,
                        // so these internal callback endpoints are open for service-to-service use.
                        .requestMatchers(HttpMethod.POST, "/api/budgets/*/approval").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/expenses/*/approval").permitAll()
                        // Service-to-service: PM reads SUBMITTED budgets/expenses to populate
                        // the pending approval list (GET /api/finance/budgets/pending on PM side
                        // calls GET /api/budgets/status/SUBMITTED on Finance).
                        .requestMatchers(HttpMethod.GET, "/api/budgets/status/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/expenses/status/**").permitAll()
                        // Only ADMIN and FINANCE_OFFICER can access the API
                        .anyRequest().hasAnyRole("ADMIN", "FINANCE_OFFICER")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

