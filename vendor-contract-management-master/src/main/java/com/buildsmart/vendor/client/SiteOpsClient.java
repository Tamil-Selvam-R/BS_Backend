package com.buildsmart.vendor.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * FEATURE SET 2 — Vendor ↔ Site Feign client.
 *
 * Two responsibilities:
 *  1. Vendor → Site PUSH: vendor notifies SiteOps when a delivery has been
 *     dispatched so the Site Officer can later confirm physical receipt.
 *  2. Site → Vendor PULL (NEW): vendor pulls the latest Site Officer outcome
 *     for a delivery so the local Delivery row stays in sync even if the
 *     original push from siteops to vendor was lost.
 *
 * Both endpoints sit under /internal/** in SiteOps and are open for
 * service-to-service calls (no JWT).
 */
@FeignClient(
        name = "siteops-service",
        contextId = "vendorSiteOpsClient",
        fallback = SiteOpsClientFallback.class
)
public interface SiteOpsClient {

    /**
     * Notify SiteOps that a delivery has been dispatched.
     * Fire-and-forget — failures must not roll back the vendor transaction.
     */
    @PostMapping("/internal/deliveries")
    void notifyDeliveryDispatched(@RequestBody DeliveryDispatchPayload payload);

    /**
     * Pull the Site Officer's confirmation for a delivery.
     *
     * Returned values:
     *  - {@code known=false}                       → siteops never received the dispatch.
     *  - {@code known=true, siteStatus=null}        → confirmation pending.
     *  - {@code known=true, siteStatus="RECEIVED"|"NOT_RECEIVED"} → confirmed.
     *
     * Vendor calls this on every read of a Delivery so the local status row
     * always reflects what the Site Officer reported, regardless of whether
     * the push from siteops to vendor succeeded.
     */
    @GetMapping("/internal/deliveries/{deliveryId}/site-status")
    SiteStatusResponse getSiteStatus(@PathVariable("deliveryId") String deliveryId);

    /**
     * Outbound payload — minimal info siteops needs to confirm receipt later.
     */
    record DeliveryDispatchPayload(
            String deliveryId,
            String contractId,
            String item,
            Integer quantity,
            LocalDate date,
            String status
    ) {}

    /**
     * Mirror of the JSON returned by siteops's
     *   GET /internal/deliveries/{id}/site-status
     * Field names match exactly so Jackson can deserialise.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteStatusResponse(
            String deliveryId,
            boolean known,
            String siteStatus,        // "RECEIVED" | "NOT_RECEIVED" | null
            String siteRemarks,
            LocalDateTime receivedAt,
            String vendorStatus
    ) {}
}
