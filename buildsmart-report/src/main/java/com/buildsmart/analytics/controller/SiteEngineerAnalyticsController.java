package com.buildsmart.analytics.controller;

import com.buildsmart.analytics.dto.SiteEngineerDailyLogRecord;
import com.buildsmart.analytics.dto.SiteEngineerPerformanceRecord;
import com.buildsmart.analytics.dto.SiteProgressSummaryRecord;
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

/**
 * Controller for site engineer analytics and reporting.
 * ADMIN and PROJECT_MANAGER can see all engineers' data.
 * SITE_ENGINEER users can only see their own data (engineerId = userId from JWT).
 */
@RestController
@RequestMapping(path = "/api/reports/site-engineer", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'SITE_ENGINEER')")
public class SiteEngineerAnalyticsController {

    private final AnalyticsService analyticsService;

    public SiteEngineerAnalyticsController(AnalyticsService analyticsService) {
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
     * Get performance metrics for all site engineers (ADMIN/PM only).
     * SITE_ENGINEER role gets their own performance only.
     */
    @GetMapping(path = "/performance")
    public List<SiteEngineerPerformanceRecord> getSiteEngineerPerformance() {
        if (isPrivilegedUser()) {
            return analyticsService.getSiteEngineerPerformance();
        }
        // SITE_ENGINEER: return only their own performance
        String engineerId = getCurrentUserId();
        if (engineerId == null) return List.of();
        try {
            SiteEngineerPerformanceRecord record = analyticsService.getSiteEngineerPerformanceById(engineerId);
            return List.of(record);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Get performance for a specific site engineer.
     * SITE_ENGINEER role can only query their own engineerId.
     */
    @GetMapping(path = "/performance/{engineerId}")
    public SiteEngineerPerformanceRecord getSiteEngineerPerformanceById(
            @PathVariable("engineerId") String engineerId) {
        if (!isPrivilegedUser()) {
            String currentUserId = getCurrentUserId();
            if (currentUserId == null || !currentUserId.equals(engineerId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access denied: you can only view your own performance.");
            }
        }
        return analyticsService.getSiteEngineerPerformanceById(engineerId);
    }

    /**
     * Get aggregated site progress summary — ADMIN/PM only.
     */
    @GetMapping(path = "/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public SiteProgressSummaryRecord getSiteProgressSummary() {
        return analyticsService.getSiteProgressSummary();
    }

    /**
     * Get daily logs for all site engineers (ADMIN/PM only).
     * SITE_ENGINEER role gets their own daily logs only.
     */
    @GetMapping(path = "/daily-logs")
    public List<SiteEngineerDailyLogRecord> getDailyLogs() {
        if (isPrivilegedUser()) {
            return analyticsService.getSiteEngineerDailyLogs();
        }
        // SITE_ENGINEER: return only their own logs
        String engineerId = getCurrentUserId();
        if (engineerId == null) return List.of();
        return analyticsService.getSiteEngineerDailyLogsByEngineer(engineerId);
    }

    /**
     * Get daily logs for a specific site engineer.
     * SITE_ENGINEER role can only query their own engineerId.
     */
    @GetMapping(path = "/daily-logs/{engineerId}")
    public List<SiteEngineerDailyLogRecord> getDailyLogsByEngineer(
            @PathVariable("engineerId") String engineerId) {
        if (!isPrivilegedUser()) {
            String currentUserId = getCurrentUserId();
            if (currentUserId == null || !currentUserId.equals(engineerId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access denied: you can only view your own daily logs.");
            }
        }
        return analyticsService.getSiteEngineerDailyLogsByEngineer(engineerId);
    }
}
