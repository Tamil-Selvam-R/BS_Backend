package com.buildsmart.siteops.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger configuration for the SiteOps (Site Engineer) service.
 *
 * UI:  http://localhost:8087/swagger-ui.html
 * Spec: http://localhost:8087/v3/api-docs
 *
 * Endpoints are protected by a JWT bearer token. Click the "Authorize" button
 * in the Swagger UI and paste the access token (without the "Bearer " prefix)
 * to invoke secured endpoints.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI siteOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BuildSmart SiteOps Service API")
                        .description("""
                                Site Engineer microservice for the BuildSmart platform.

                                Provides APIs for:
                                  • Daily site logs and review workflow
                                  • Issue reporting and resolution (with PM allocation)
                                  • Assigned tasks (synced from PM TASK_ASSIGNED notifications)
                                  • Resource requests and PM approval callbacks
                                  • Notifications

                                Authentication: All `/api/**` endpoints require a JWT bearer token
                                obtained from the IAM service login endpoint.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BuildSmart Engineering")
                                .email("engineering@buildsmart.local"))
                        .license(new License()
                                .name("Proprietary — BuildSmart Internal")))
                .servers(List.of(
                        new Server().url("http://localhost:8087").description("Local SiteOps service"),
                        new Server().url("http://localhost:8080").description("API Gateway")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste the JWT access token issued by the IAM login endpoint.")));
    }
}
