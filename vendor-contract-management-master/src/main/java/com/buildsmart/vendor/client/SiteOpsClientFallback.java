package com.buildsmart.vendor.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for SiteOpsClient — logs and swallows failures so a down SiteOps
 * never blocks vendor delivery creation. The vendor record is kept in the
 * vendor DB regardless; siteops can be reconciled later.
 */
@Component
public class SiteOpsClientFallback implements SiteOpsClient {

    private static final Logger log = LoggerFactory.getLogger(SiteOpsClientFallback.class);

    @Override
    public void notifyDeliveryDispatched(DeliveryDispatchPayload payload) {
        log.warn("siteops-service unavailable — dispatch event dropped for delivery '{}'.",
                payload != null ? payload.deliveryId() : "null");
    }

    @Override
    public SiteStatusResponse getSiteStatus(String deliveryId) {
        log.warn("siteops-service unavailable — cannot pull site status for delivery '{}'. "
                + "Vendor will keep its current local status until siteops is reachable.", deliveryId);
        // known=false signals to the caller that no remote info is available;
        // local state must be left untouched.
        return new SiteStatusResponse(deliveryId, false, null, null, null, null);
    }
}
