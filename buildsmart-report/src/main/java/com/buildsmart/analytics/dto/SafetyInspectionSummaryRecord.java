package com.buildsmart.analytics.dto;

/**
 * Summary of site inspections grouped by status.
 */
public record SafetyInspectionSummaryRecord(
        long scheduled,
        long inProgress,
        long completed,
        long nonCompliant,
        long closed,
        long total
) {}
