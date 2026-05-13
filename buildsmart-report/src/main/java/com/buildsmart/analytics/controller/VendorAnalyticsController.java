package com.buildsmart.analytics.controller;

import com.buildsmart.analytics.dto.VendorComplianceRecord;
import com.buildsmart.analytics.dto.VendorPerformanceRecord;
import com.buildsmart.analytics.service.AnalyticsService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(path = "/api/reports/vendor", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VENDOR')")
public class VendorAnalyticsController {

    private final AnalyticsService analyticsService;

    public VendorAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    private boolean isPrivilegedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PROJECT_MANAGER"));
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth.getDetails();
        return details != null ? details.toString() : null;
    }

    /**
     * Get performance metrics for all vendors (ADMIN/PM only).
     * VENDOR role gets their own performance only.
     */
    @GetMapping(path = "/performance")
    public List<VendorPerformanceRecord> getVendorPerformance() {
        if (isPrivilegedUser()) {
            return analyticsService.getVendorPerformance();
        }
        // VENDOR: return only their own performance
        String vendorId = getCurrentUserId();
        if (vendorId == null) return List.of();
        try {
            VendorPerformanceRecord record = analyticsService.getVendorPerformanceById(vendorId);
            return List.of(record);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Get performance metrics for a specific vendor.
     * VENDOR role can only query their own vendorId.
     */
    @GetMapping(path = "/performance/{vendorId}")
    public VendorPerformanceRecord getVendorPerformanceById(@PathVariable("vendorId") String vendorId) {
        if (!isPrivilegedUser()) {
            String currentUserId = getCurrentUserId();
            if (currentUserId == null || !currentUserId.equals(vendorId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access denied: you can only view your own vendor performance.");
            }
        }
        return analyticsService.getVendorPerformanceById(vendorId);
    }

    /**
     * Get vendor compliance summary — ADMIN/PM only.
     *
     * <p>Sourced from the Vendor module via Feign:
     * vendors + contracts + documents (APPROVED / REJECTED / PENDING / SUBMITTED)
     * are fetched and aggregated into a single {@link VendorComplianceRecord}.</p>
     */
    @GetMapping(path = "/compliance")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public VendorComplianceRecord getVendorCompliance() {
        return analyticsService.getVendorCompliance();
    }
}
