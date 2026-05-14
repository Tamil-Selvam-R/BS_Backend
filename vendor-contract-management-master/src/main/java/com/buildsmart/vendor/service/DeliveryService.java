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
     */
    DeliveryResponse confirmFromSite(String deliveryId, DeliveryStatus status, String remarks);

    /**
     * Manually pull the latest site outcome from siteops for a single delivery
     * and apply it to the local row.
     */
    DeliveryResponse pullSiteStatus(String deliveryId);

    /**
     * Paginated deliveries scoped to a vendor's own contracts.
     * Used when VENDOR role calls GET /api/deliveries.
     */
    Page<DeliveryResponse> getDeliveriesByContractIds(List<String> contractIds, Pageable pageable);

    /**
     * Deliveries for a vendor's contracts filtered by status.
     * Used when VENDOR role calls GET /api/deliveries/status/{status}.
     */
    List<DeliveryResponse> getDeliveriesByContractIdsAndStatus(List<String> contractIds, DeliveryStatus status);
}
