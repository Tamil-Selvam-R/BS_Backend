package com.buildsmart.siteops.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * FEATURE SET 2 — Site → Vendor Feign client.
 *
 * Used to push the Site Officer's RECEIVED / NOT_RECEIVED outcome back to the
 * vendor service so the vendor's delivery row reflects the physical reality.
 *
 * The vendor endpoint is service-to-service (open in vendor SecurityConfig).
 */
@FeignClient(
        name = "vendor-service",
        contextId = "siteopsVendorClient"
)
public interface VendorClient {

    /**
     * Notify the vendor that the Site Officer has confirmed (or denied)
     * physical receipt of a delivery.
     *
     * @param deliveryId vendor's delivery id
     * @param status     "RECEIVED" or "NOT_RECEIVED"
     * @param remarks    optional free-text remarks (e.g. damage description)
     */
    @PatchMapping("/api/vendor-integration/deliveries/{deliveryId}/site-status")
    void updateDeliverySiteStatus(@PathVariable("deliveryId") String deliveryId,
                                  @RequestParam("status") String status,
                                  @RequestParam(value = "remarks", required = false) String remarks);
}
