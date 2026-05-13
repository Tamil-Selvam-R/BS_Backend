package com.buildsmart.analytics.client;

import java.math.BigDecimal;

public record BudgetDTO(
    String budgetId,
    String projectId,
    String category,
    BigDecimal plannedAmount,
    BigDecimal actualAmount,
    BigDecimal variance
) {}
