package com.buildsmart.siteops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * FEATURE SET 2 — Light-weight mirror of a vendor Delivery row stored on the
 * Site side so the Site Officer can see incoming deliveries in their portal
 * and confirm physical receipt.
 *
 * The vendor's Delivery row remains the system of record; this entity is
 * append-only metadata and a status mirror.
 */
@Entity
@Table(name = "inbound_deliveries")
@Getter
@Setter
public class InboundDelivery {

    /** Vendor's deliveryId — used as the natural key to keep the model thin. */
    @Id
    @Column(name = "delivery_id", length = 30, nullable = false, updatable = false)
    private String deliveryId;

    @Column(name = "contract_id", length = 30)
    private String contractId;

    @Column(name = "item", length = 200)
    private String item;

    @Column(name = "quantity")
    private Integer quantity;

    /** Date the vendor recorded the delivery. */
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    /** Free-text status mirror. Vendor enum values (PENDING, IN_TRANSIT, ...). */
    @Column(name = "vendor_status", length = 30)
    private String vendorStatus;

    /** Site outcome: NULL until the Site Officer confirms; then RECEIVED / NOT_RECEIVED. */
    @Column(name = "site_status", length = 30)
    private String siteStatus;

    /** Site Officer remarks captured at confirmation time. */
    @Column(name = "site_remarks", length = 1000)
    private String siteRemarks;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) registeredAt = LocalDateTime.now();
    }
}
