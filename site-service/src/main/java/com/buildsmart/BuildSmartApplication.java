package com.buildsmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.buildsmart")
@EnableFeignClients(basePackages = "com.buildsmart.siteops.client")
public class BuildSmartApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuildSmartApplication.class, args);
    }
}
