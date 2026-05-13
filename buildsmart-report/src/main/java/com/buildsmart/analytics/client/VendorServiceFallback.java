package com.buildsmart.analytics.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for VendorServiceClient when the Vendor service is unavailable.
 * Returns realistic dummy data so the analytics module can still function.
 */
@Component
public class VendorServiceFallback implements VendorServiceClient {

    private static final Logger log = LoggerFactory.getLogger(VendorServiceFallback.class);

    @Override
    public List<VendorDTO> getAllVendors() {
        log.warn("Vendor service unavailable — returning fallback vendor list");
        return List.of(
                new VendorDTO("VND001", "Titan Steel Suppliers", "titan@buildsmart.com", "900000001", "ACTIVE"),
                new VendorDTO("VND002", "QuickMix Concrete", "quickmix@buildsmart.com", "900000002", "ACTIVE"),
                new VendorDTO("VND003", "ElectraPower Solutions", "electra@buildsmart.com", "900000003", "PENDING"),
                new VendorDTO("VND004", "SafeGuard Equipment", "safeguard@buildsmart.com", "900000004", "BLACKLISTED")
        );
    }

    @Override
    public VendorDTO getVendorById(String vendorId) {
        log.warn("Vendor service unavailable — returning fallback for vendorId: {}", vendorId);
        return new VendorDTO(vendorId, "Unknown Vendor", "unknown@buildsmart.com", "N/A", "PENDING");
    }

    @Override
    public List<VendorDTO> getVendorsByStatus(String status) {
        log.warn("Vendor service unavailable — returning fallback vendors for status={}", status);
        return getAllVendors().stream().filter(v -> status.equalsIgnoreCase(v.status())).toList();
    }

    @Override
    public List<VendorContractDTO> getAllContracts() {
        log.warn("Vendor service unavailable — returning fallback contract list");
        return List.of(
                new VendorContractDTO("C001", "VND001", "P-1001", LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(4), new BigDecimal("250000"), "ACTIVE"),
                new VendorContractDTO("C002", "VND002", "P-1002", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6), new BigDecimal("180000"), "ACTIVE"),
                new VendorContractDTO("C003", "VND003", "P-1003", LocalDate.now().minusMonths(3), LocalDate.now().minusDays(5), new BigDecimal("90000"), "COMPLETED")
        );
    }

    @Override
    public List<VendorContractDTO> getContractsByStatus(String status) {
        log.warn("Vendor service unavailable — returning fallback contracts for status={}", status);
        return getAllContracts().stream().filter(c -> status.equalsIgnoreCase(c.status())).toList();
    }

    @Override
    public List<VendorDeliveryDTO> getAllDeliveries() {
        log.warn("Vendor service unavailable — returning fallback delivery list");
        return List.of(
                new VendorDeliveryDTO("D001", "C001", LocalDate.now().minusDays(7), "Steel", 100, "DELIVERED"),
                new VendorDeliveryDTO("D002", "C001", LocalDate.now().minusDays(2), "Steel", 80, "IN_TRANSIT"),
                new VendorDeliveryDTO("D003", "C002", LocalDate.now().minusDays(4), "Cement", 120, "RECEIVED"),
                new VendorDeliveryDTO("D004", "C002", LocalDate.now().minusDays(1), "Cement", 50, "PENDING")
        );
    }

    @Override
    public List<VendorDeliveryDTO> getDeliveriesByStatus(String status) {
        log.warn("Vendor service unavailable — returning fallback deliveries for status={}", status);
        return getAllDeliveries().stream().filter(d -> status.equalsIgnoreCase(d.status())).toList();
    }

    @Override
    public List<VendorInvoiceDTO> getInvoicesByStatus(String status) {
        log.warn("Vendor service unavailable — returning fallback invoices for status={}", status);
        // Return a small sample so analytics still produces non-zero numbers when vendor is down.
        if ("APPROVED".equalsIgnoreCase(status)) {
            return List.of(
                    new VendorInvoiceDTO("INV001", "C001", "APR-1", "T-1", new BigDecimal("120000"),
                            LocalDate.now().minusDays(20), "APPROVED", "PM001", null, null,
                            "Steel batch 1", "VND001", LocalDateTime.now().minusDays(25),
                            LocalDateTime.now().minusDays(20), null),
                    new VendorInvoiceDTO("INV002", "C002", "APR-2", "T-2", new BigDecimal("90000"),
                            LocalDate.now().minusDays(10), "APPROVED", "PM001", null, null,
                            "Cement batch 1", "VND002", LocalDateTime.now().minusDays(15),
                            LocalDateTime.now().minusDays(10), null)
            );
        }
        if ("REJECTED".equalsIgnoreCase(status)) {
            return List.of(
                    new VendorInvoiceDTO("INV003", "C003", "APR-3", "T-3", new BigDecimal("15000"),
                            LocalDate.now().minusDays(5), "REJECTED", null, "PM001",
                            "Amount exceeds contract value", "Cabling", "VND003",
                            LocalDateTime.now().minusDays(8), null, LocalDateTime.now().minusDays(5))
            );
        }
        return List.of();
    }

    @Override
    public List<VendorDocumentDTO> getDocumentsByStatus(String status) {
        log.warn("Vendor service unavailable — returning fallback documents for status={}", status);
        if ("APPROVED".equalsIgnoreCase(status)) {
            return List.of(
                    new VendorDocumentDTO("DOC001", "VND001", "APR-D1", null, "P-1001", "C001",
                            "Insurance.pdf", "INSURANCE", "/files/d1.pdf", 2048L, "VND001",
                            "Liability insurance", LocalDateTime.now().minusDays(40), "APPROVED",
                            "PM001", null, null),
                    new VendorDocumentDTO("DOC002", "VND002", "APR-D2", null, "P-1002", "C002",
                            "ISO.pdf", "CERTIFICATION", "/files/d2.pdf", 1024L, "VND002",
                            "ISO 9001", LocalDateTime.now().minusDays(60), "APPROVED",
                            "PM001", null, null)
            );
        }
        if ("REJECTED".equalsIgnoreCase(status)) {
            return List.of(
                    new VendorDocumentDTO("DOC003", "VND003", "APR-D3", null, "P-1003", "C003",
                            "License.pdf", "LICENSE", "/files/d3.pdf", 512L, "VND003",
                            "Expired license", LocalDateTime.now().minusDays(20), "REJECTED",
                            null, "PM001", "License is expired")
            );
        }
        return List.of();
    }
}
