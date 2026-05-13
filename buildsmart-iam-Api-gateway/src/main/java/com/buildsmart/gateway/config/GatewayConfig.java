package com.buildsmart.gateway.config;

// Disabled: routes are defined in application.properties as the single source of truth.
// The previous Java route definitions used `lb://project-manager-service`, which is wrong
// — the actual Eureka name is `project-service`. Class kept (empty) so any external
// reference to the package compiles.
public class GatewayConfig {
}
