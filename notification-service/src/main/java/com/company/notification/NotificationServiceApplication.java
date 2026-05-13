package com.company.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Notification Service entry point.
 *
 * Design notes:
 *  - One service, one controller. RBAC filtering is enforced on the server.
 *  - Feign is enabled so OTHER services can be called if needed (e.g., user lookup).
 *    Other microservices use their own Feign client to call THIS service's
 *    POST /notifications endpoint to publish a notification.
 *  - JPA auditing populates createdAt automatically.
 *  - Async support is enabled so the controller can return fast even when
 *    the service does post-processing (future-proofing for Kafka / WebSocket).
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.company.notification.client")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableAsync
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
