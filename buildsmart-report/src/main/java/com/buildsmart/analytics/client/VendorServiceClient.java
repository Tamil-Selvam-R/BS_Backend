package com.buildsmart.analytics.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to communicate with the Vendor Management microservice.
 *
 * The vendor module exposes status-based GETs as permitAll so the analytics
 * module can call them service-to-service without a user JWT. We aggregate
 * across all relevant statuses to build a complete picture for:
 *   - /api/reports/vendor/spend         (uses contracts + invoices)
 *   - /api/reports/vendor/performance   (uses contracts + deliveries + invoices)
 *   - /api/reports/vendor/compliance    (uses vendors + contracts + documents)
 */
@FeignClient(
        name = "vendor-service",
        fallback = VendorServiceFallback.class,
        url = "${vendor.service.url}"
)
public interface VendorServiceClient {

    // ── Vendors ───────────────────────────────────────────────────────────────

    @GetMapping("/api/vendors")
    List<VendorDTO> getAllVendors();

    @GetMapping("/api/vendors/{id}")
    VendorDTO getVendorById(@PathVariable("id") String vendorId);

    @GetMapping("/api/vendors/status/{status}")
    List<VendorDTO> getVendorsByStatus(@PathVariable("status") String status);

    // ── Contracts ─────────────────────────────────────────────────────────────

    @GetMapping("/api/contracts")
    List<VendorContractDTO> getAllContracts();

    /** Used as a permitAll fallback for environments where /api/contracts requires auth. */
    @GetMapping("/api/contracts/status/{status}")
    List<VendorContractDTO> getContractsByStatus(@PathVariable("status") String status);

    // ── Deliveries ────────────────────────────────────────────────────────────

    @GetMapping("/api/deliveries")
    List<VendorDeliveryDTO> getAllDeliveries();

    @GetMapping("/api/deliveries/status/{status}")
    List<VendorDeliveryDTO> getDeliveriesByStatus(@PathVariable("status") String status);

    // ── Invoices ──────────────────────────────────────────────────────────────

    /**
     * Vendor InvoiceStatus values: PENDING, SUBMITTED, APPROVED, REJECTED, PAID, OVERDUE, CANCELLED.
     * Used by /spend (real APPROVED+PAID amounts) and /performance (approval rate).
     */
    @GetMapping("/api/invoices/status/{status}")
    List<VendorInvoiceDTO> getInvoicesByStatus(@PathVariable("status") String status);

    // ── Documents ─────────────────────────────────────────────────────────────

    /**
     * Vendor DocumentStatus values: PENDING, SUBMITTED, APPROVED, REJECTED.
     * Used by /compliance (per-vendor document approval rate).
     */
    @GetMapping("/api/documents/status/{status}")
    List<VendorDocumentDTO> getDocumentsByStatus(@PathVariable("status") String status);
}
