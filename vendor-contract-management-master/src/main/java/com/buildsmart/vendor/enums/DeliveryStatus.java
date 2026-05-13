package com.buildsmart.vendor.enums;

/**
 * Delivery lifecycle.
 *
 * FEATURE SET 2 added two terminal states reported by the Site service:
 *   - RECEIVED      : Site Officer confirmed the items physically arrived.
 *   - NOT_RECEIVED  : Site Officer reports the items did not arrive / are missing.
 *
 * Existing states (PENDING, IN_TRANSIT, DELIVERED, CANCELLED) are unchanged
 * for backward compatibility.
 */
public enum DeliveryStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    RECEIVED,
    NOT_RECEIVED
}

