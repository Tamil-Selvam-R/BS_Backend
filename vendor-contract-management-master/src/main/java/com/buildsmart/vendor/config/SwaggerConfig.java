package com.buildsmart.vendor.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI vendorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BuildSmart - Vendor Contract Management API")
                        .description("REST API for managing vendors, contracts, invoices, and deliveries. "
                                + "Login as VENDOR or PROJECT_MANAGER and paste the JWT token using the Authorize button.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BuildSmart Team")
                                .email("support@buildsmart.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Token"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Token", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token obtained after login. "
                                        + "Role: VENDOR or PROJECT_MANAGER")));
    }
}

