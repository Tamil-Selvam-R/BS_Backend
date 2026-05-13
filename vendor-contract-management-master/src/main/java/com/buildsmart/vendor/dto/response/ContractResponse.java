package com.buildsmart.vendor.dto.response;

import com.buildsmart.vendor.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ContractResponse {
    private String contractId;

    private String vendorId;

    private String projectId;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal value;

    private ContractStatus status;

    private String taskId;
    private String description;

    public ContractResponse() {
    }

    public ContractResponse(String contractId, String vendorId, String projectId, LocalDate startDate, LocalDate endDate, BigDecimal value, ContractStatus status, String taskId, String description) {
        this.contractId = contractId;
        this.vendorId = vendorId;
        this.projectId = projectId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.value = value;
        this.status = status;
        this.taskId = taskId;
        this.description = description;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

