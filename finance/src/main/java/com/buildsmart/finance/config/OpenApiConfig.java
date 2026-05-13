package com.buildsmart.finance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeOpenAPI() {

        // Define JWT Bearer Authentication scheme
        SecurityScheme bearerAuthScheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                // API basic information
                .info(new Info()
                        .title("Finance Service API")
                        .description("Finance Service for Budget, Expense, and Payment Management")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BuildSmart Finance Team")
                                .email("finance@buildsmart.com")
                        )
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")
                        )
                )
                // Add security requirement (global)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                // Register security scheme
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerAuthScheme)
                );
    }
}