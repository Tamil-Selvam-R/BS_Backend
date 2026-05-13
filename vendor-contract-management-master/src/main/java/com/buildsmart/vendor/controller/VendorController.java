package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.entity.Contract;
import com.buildsmart.vendor.enums.ContractStatus;
import com.buildsmart.vendor.repository.ContractRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only Vendor list endpoints exposed for the analytics / buildsmart-report
 * microservice's {@code VendorServiceClient}.
 *
 * <p>The vendor-service does NOT have a Vendor master entity — vendors are
 * referenced only by {@code vendorId} on contracts. This controller derives a
 * minimal "vendor view" from contract data so that:
 *   - {@code GET /api/vendors}                — returns every distinct vendorId
 *   - {@code GET /api/vendors/{id}}           — returns the single vendor view
 *   - {@code GET /api/vendors/status/{status}} — filters by aggregated contract status
 *
 * <p>Each response item is a flat Map (instead of a dedicated DTO) so the shape
 * stays compatible with the analytics service's {@code VendorDTO} record
 * fields (vendorId, name, email, contactInfo, status). Fields with no source
 * data in this service are returned as {@code null}.
 *
 * <p>Authentication: opened in SecurityConfig under {@code /api/vendors/**}
 * because analytics calls this without forwarding a user JWT.
 */
@RestController
@RequestMapping("/api/vendors")
@Tag(name = "Vendors (derived)",
        description = "Read-only vendor list derived from contract data. "
                + "Consumed by analytics / report-management service.")
public class VendorController {

    private final ContractRepository contractRepository;

    public VendorController(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * Distinct vendors known to the vendor-service, derived from contracts.
     * Status is computed as the most recent contract's status for that vendor
     * (best-effort — contracts are unsorted, so we use the first occurrence).
     */
    @GetMapping
    @Operation(summary = "List all vendors known to the vendor-service (derived from contracts).")
    public ResponseEntity<List<Map<String, Object>>> getAllVendors() {
        List<String> vendorIds = contractRepository.findDistinctVendorIds();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String vendorId : vendorIds) {
            result.add(buildVendorView(vendorId));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Single vendor view, looked up by the same vendorId used on contracts.
     * Returns 404 if the vendor has no contracts in this service.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a single vendor view by vendorId.")
    public ResponseEntity<Map<String, Object>> getVendorById(@PathVariable("id") String vendorId) {
        List<Contract> contracts = contractRepository.findByVendorId(vendorId);
        if (contracts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildVendorView(vendorId, contracts));
    }

    /**
     * Vendors filtered by aggregated contract status.
     * A vendor is included if at least one of their contracts is in the requested status.
     * Invalid status returns an empty list (analytics treats this as "no vendors").
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "List vendors with at least one contract in the given status.")
    public ResponseEntity<List<Map<String, Object>>> getVendorsByStatus(@PathVariable("status") String status) {
        ContractStatus parsed;
        try {
            parsed = ContractStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(List.of());
        }
        // Group contracts by vendorId so each vendor appears once per status.
        Map<String, List<Contract>> grouped = new LinkedHashMap<>();
        for (Contract c : contractRepository.findByStatus(parsed)) {
            if (c.getVendorId() == null) continue;
            grouped.computeIfAbsent(c.getVendorId(), k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Contract>> e : grouped.entrySet()) {
            result.add(buildVendorView(e.getKey(), e.getValue()));
        }
        return ResponseEntity.ok(result);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildVendorView(String vendorId) {
        return buildVendorView(vendorId, contractRepository.findByVendorId(vendorId));
    }

    /**
     * Build a flat Map matching the analytics-side VendorDTO shape:
     *   { vendorId, name, email, contactInfo, status }.
     * name / email / contactInfo are null because vendor-service has no
     * Vendor master record — analytics tolerates nulls.
     */
    private Map<String, Object> buildVendorView(String vendorId, List<Contract> contracts) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("vendorId", vendorId);
        view.put("name", null);
        view.put("email", null);
        view.put("contactInfo", null);
        view.put("status", computeAggregateStatus(contracts));
        return view;
    }

    /**
     * Aggregated status: ACTIVE if any contract is ACTIVE, otherwise the most
     * recent status seen, or "UNKNOWN" if there are no contracts at all.
     */
    private String computeAggregateStatus(List<Contract> contracts) {
        if (contracts == null || contracts.isEmpty()) return "UNKNOWN";
        for (Contract c : contracts) {
            if (c.getStatus() != null && c.getStatus().name().equalsIgnoreCase("ACTIVE")) {
                return "ACTIVE";
            }
        }
        return contracts.get(0).getStatus() != null
                ? contracts.get(0).getStatus().name()
                : "UNKNOWN";
    }
}
