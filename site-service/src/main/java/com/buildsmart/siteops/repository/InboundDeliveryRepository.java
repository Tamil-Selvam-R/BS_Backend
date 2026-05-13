package com.buildsmart.siteops.repository;

import com.buildsmart.siteops.entity.InboundDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * FEATURE SET 2 — repository for inbound deliveries received from the vendor.
 */
public interface InboundDeliveryRepository extends JpaRepository<InboundDelivery, String> {

    /** Used by the Site Officer's "incoming deliveries" view. */
    List<InboundDelivery> findBySiteStatusIsNull();

    /** Filter by contract for vendor-to-site reconciliation. */
    List<InboundDelivery> findByContractIdOrderByRegisteredAtDesc(String contractId);
}
