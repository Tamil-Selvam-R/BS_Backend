package com.buildsmart.projectmanager.config;

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
import com.buildsmart.projectmanager.security.JwtAuthenticationFilter;

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
                // Service-to-service endpoints (called by Safety/SiteOps/Finance/Vendor via Feign)
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/notifications/**").hasAnyRole("ADMIN", "PROJECT_MANAGER", "VENDOR", "SITE_ENGINEER", "SAFETY_OFFICER", "FINANCE_OFFICER")
                .requestMatchers(HttpMethod.GET, "/projects/{projectId}").hasAnyRole("ADMIN", "PROJECT_MANAGER", "SITE_ENGINEER", "SAFETY_OFFICER", "FINANCE_OFFICER", "VENDOR")
                // Submitting an approval request: vendors push invoices/documents,
                // site engineers push daily site logs, finance/safety push their own
                // approvals via Feign with their own JWT — all need to POST /api/approvals.
                // Read-side approval endpoints (GET /approvals, /pending, etc.) and
                // approve/reject actions remain restricted to ADMIN/PROJECT_MANAGER below.
                .requestMatchers(HttpMethod.POST, "/approvals").hasAnyRole(
                        "ADMIN", "PROJECT_MANAGER", "VENDOR",
                        "SITE_ENGINEER", "SAFETY_OFFICER", "FINANCE_OFFICER")
                .anyRequest().hasAnyRole("ADMIN", "PROJECT_MANAGER")
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
