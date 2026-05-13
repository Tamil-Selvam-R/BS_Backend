package com.buildsmart.analytics.client;

/**
 * DTO for vendor data received from the Vendor Management microservice.
 */
public record VendorDTO(
        String vendorId,
        String name,
        String email,
        String contactInfo,
        String status
) {
}
