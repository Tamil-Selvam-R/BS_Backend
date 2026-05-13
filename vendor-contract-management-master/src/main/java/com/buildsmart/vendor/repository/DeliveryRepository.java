package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.enums.DeliveryStatus;
import com.buildsmart.vendor.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, String> {
    List<Delivery> findByContractId(String contractId);
    List<Delivery> findByStatus(DeliveryStatus status);
    Optional<Delivery> findTopByOrderByDeliveryIdDesc();
}
