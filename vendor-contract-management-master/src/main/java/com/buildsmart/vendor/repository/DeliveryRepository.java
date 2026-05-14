package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.enums.DeliveryStatus;
import com.buildsmart.vendor.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, String> {
    List<Delivery> findByContractId(String contractId);
    List<Delivery> findByStatus(DeliveryStatus status);
    Optional<Delivery> findTopByOrderByDeliveryIdDesc();

    /**
     * Paginated deliveries for a set of contractIds.
     * Used when VENDOR role calls GET /api/deliveries — vendor sees only deliveries
     * belonging to their own contracts.
     */
    Page<Delivery> findByContractIdIn(List<String> contractIds, Pageable pageable);

    /**
     * All deliveries for a set of contractIds (non-paginated).
     * Used for ownership checks on list endpoints.
     */
    List<Delivery> findByContractIdIn(List<String> contractIds);

    /**
     * Deliveries for a set of contractIds filtered by status.
     * Used when VENDOR role calls GET /api/deliveries/status/{status}.
     */
    List<Delivery> findByContractIdInAndStatus(List<String> contractIds, DeliveryStatus status);
}
