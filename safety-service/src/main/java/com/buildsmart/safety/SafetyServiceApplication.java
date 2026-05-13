package com.buildsmart.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableFeignClients
public class SafetyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafetyServiceApplication.class, args);
    }

    @Slf4j
    @Component
    static class StartupLogger {

        @Value("${server.port:8083}")
        private int port;

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("    Safety Service started successfully");
            log.info("    Local:   http://localhost:{}", port);
            log.info("    Swagger: http://localhost:{}/swagger-ui/index.html", port);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }
    }
}
