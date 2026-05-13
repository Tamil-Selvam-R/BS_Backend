package com.buildsmart.analytics.client;

import java.time.LocalDate;

public record VendorDeliveryDTO(
        String deliveryId,
        String contractId,
        LocalDate date,
        String item,
        Integer quantity,
        String status
) {
}

