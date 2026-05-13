package com.buildsmart.vendor.dto.response;

import com.buildsmart.vendor.enums.DeliveryStatus;

import java.time.LocalDate;

public class DeliveryResponse {
    private String deliveryId;

    private String contractId;

    private LocalDate date;

    private String item;

    private Integer quantity;

    private DeliveryStatus status;

    public DeliveryResponse() {
    }

    public DeliveryResponse(String deliveryId, String contractId, LocalDate date, String item, Integer quantity, DeliveryStatus status) {
        this.deliveryId = deliveryId;
        this.contractId = contractId;
        this.date = date;
        this.item = item;
        this.quantity = quantity;
        this.status = status;
    }
    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

}
