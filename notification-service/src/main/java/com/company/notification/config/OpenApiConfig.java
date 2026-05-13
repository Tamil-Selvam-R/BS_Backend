package com.company.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Swagger UI for notification-service.
 *
 * Adds a JWT bearer-auth scheme so the "Authorize" button in Swagger UI
 * lets devs paste any user's JWT and impersonate them when calling
 * GET /notifications, GET /notifications/unread-count, PUT /notifications/{id}/read.
 *
 * The bell endpoints enforce per-user RBAC at the SQL layer
 * (WHERE n.toUserId = :userId), so authorizing as a different user
 * automatically shows that user's feed only.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenAPI() {
        final String schemeName = "bearer-jwt";

        return new OpenAPI()
                .info(new Info()
                        .title("BuildSmart Notification Service")
                        .description("Central per-user notification feed. " +
                                "Use Authorize to paste a user's JWT and call /notifications as that user.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName,
                                new SecurityScheme()
                                        .name(schemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste the JWT from IAM /auth/login " +
                                                "(without the 'Bearer ' prefix).")));
    }
}