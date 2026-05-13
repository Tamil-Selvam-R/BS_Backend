package com.buildsmart.analytics.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for the Finance microservice.
 * Eureka-registered name: "finance-service" (verified in finance/src/main/resources/application.properties).
 * The {@code url} property still wins when explicitly set, allowing the analytics service to
 * point at a non-Eureka deployment for local debugging.
 */
@FeignClient(name = "finance-service", fallback = FinanceServiceFallback.class, url = "${finance.service.url}")
public interface FinanceServiceClient {

    /**
     * Calls Finance's BudgetController endpoint:
     * GET /api/budgets/projects/{projectId}
     * (corrected from the previous /api/finance/budgets/project/{projectId} which never matched.)
     */
    @GetMapping("/api/budgets/projects/{projectId}")
    List<BudgetDTO> getBudgetsByProject(@PathVariable("projectId") String projectId);
}
