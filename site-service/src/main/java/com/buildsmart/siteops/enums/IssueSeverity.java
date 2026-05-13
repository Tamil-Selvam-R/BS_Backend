package com.buildsmart.siteops.enums;

/**
 * Severity levels for site issues.
 * LOW    - Minor observation; no immediate action needed.
 * MEDIUM - Work may be impacted; address within 24 hours.
 * HIGH   - Significant risk to schedule or safety; address immediately.
 * CRITICAL - Stop-work condition; escalate to PM and Safety Officer now.
 */
public enum IssueSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
