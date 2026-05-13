package com.buildsmart.analytics.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendorContractDTO(
        String contractId,
        String vendorId,
        String projectId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal value,
        String status
) {
}

