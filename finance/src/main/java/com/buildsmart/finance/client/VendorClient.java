package com.buildsmart.finance.client;

import com.buildsmart.finance.client.dto.InvoiceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for the Vendor Microservice.
 * Used by Finance to fetch invoice details (amount, status, approvedBy)
 * during payment / expense validation flows.
 *
 * <p>BUG FIX: the previous declaration used the URL placeholder {@code /{Id}}
 * with a parameter named {@code invoiceId} and no explicit {@code @PathVariable}
 * binding. Spring Cloud OpenFeign could not match the placeholder, so the URL
 * was rendered with a literal {@code {Id}} segment and the call failed with
 * a 400/404 from the vendor service. Both the path placeholder and the
 * {@code @PathVariable} annotation now use the same name {@code invoiceId}.</p>
 */
@FeignClient(
        name = "vendor-service",
        contextId = "financeVendorClient",
        fallback = VendorClientFallback.class
)
public interface VendorClient {

    /**
     * Fetch invoice details by invoiceId.
     * Vendor endpoint: GET /api/invoices/{id}
     *
     * @param invoiceId  invoice id (e.g. INV-2024-001)
     * @param authHeader Authorization header with Bearer token (forwarded to vendor)
     * @return InvoiceDto with id, vendorId, amount, approvedBy, status
     */
    @GetMapping("/api/invoices/{invoiceId}")
    InvoiceDto getInvoice(
            @PathVariable("invoiceId") String invoiceId,
            @RequestHeader("Authorization") String authHeader
    );

    /**
     * Fetch invoices filtered by status (e.g. APPROVED) — used by Finance to
     * locate approved vendor invoices that should be settled with a Payment.
     * Vendor endpoint: GET /api/invoices/status/{status}
     */
    @GetMapping("/api/invoices/status/{status}")
    List<InvoiceDto> getInvoicesByStatus(
            @PathVariable("status") String status,
            @RequestHeader("Authorization") String authHeader
    );
}

