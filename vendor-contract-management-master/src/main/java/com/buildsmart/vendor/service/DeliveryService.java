package com.buildsmart.vendor.service;

import com.buildsmart.vendor.dto.response.DeliveryResponse;
import com.buildsmart.vendor.dto.request.DeliveryRequest;
import com.buildsmart.vendor.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DeliveryService {
    Page<DeliveryResponse> getAllDeliveries(Pageable pageable);
    DeliveryResponse getDeliveryById(String id);
    List<DeliveryResponse> getDeliveriesByContractId(String contractId);
    List<DeliveryResponse> getDeliveriesByStatus(DeliveryStatus status);
    DeliveryResponse createDelivery(DeliveryRequest request);
    DeliveryResponse updateDelivery(String id, DeliveryRequest request);
    void deleteDelivery(String id);

    /**
     * FEATURE SET 2 step 2 — Site → Vendor callback.
     * SiteOps calls this when the Site Officer confirms (or denies) physical
     * receipt of a delivery. The vendor delivery row's status is updated to
     * RECEIVED or NOT_RECEIVED accordingly.
     *
     * @param deliveryId vendor's delivery id
     * @param status     RECEIVED or NOT_RECEIVED
     * @param remarks    optional site remarks (e.g. damage description)
     */
    DeliveryResponse confirmFromSite(String deliveryId, DeliveryStatus status, String remarks);

    /**
     * Manually pull the latest site outcome from siteops for a single delivery
     * and apply it to the local row. Same effect as the implicit sync that
     * runs on every {@code getDeliveryById}, but exposed as an explicit action
     * for vendor admins who want to force a refresh without waiting for a read.
     */
    DeliveryResponse pullSiteStatus(String deliveryId);
}
