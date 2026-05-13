package com.buildsmart.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class FinanceServiceFallback implements FinanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceServiceFallback.class);

    @Override
    public List<BudgetDTO> getBudgetsByProject(String projectId) {
        log.warn("[Fallback][FinanceService] getBudgetsByProject({}) — downstream unavailable, returning empty list", projectId);
        return List.of();
    }
}
