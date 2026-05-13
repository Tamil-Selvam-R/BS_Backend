package com.buildsmart.finance.client;

import com.buildsmart.finance.client.dto.InvoiceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resilience4j circuit-breaker fallback for {@link VendorClient}.
 * Returns null / empty list when the Vendor service is unreachable — this
 * prevents an unhandled FeignException from breaking the Finance flow that
 * just wanted to look up an invoice's amount or status.
 */
@Slf4j
@Component
public class VendorClientFallback implements VendorClient {

    @Override
    public InvoiceDto getInvoice(String invoiceId, String authHeader) {
        log.warn("[Fallback][VendorClient] getInvoice({}) — vendor-service unreachable.", invoiceId);
        return null;
    }

    @Override
    public List<InvoiceDto> getInvoicesByStatus(String status, String authHeader) {
        log.warn("[Fallback][VendorClient] getInvoicesByStatus({}) — vendor-service unreachable.", status);
        return List.of();
    }
}
