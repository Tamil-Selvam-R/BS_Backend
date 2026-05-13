package com.buildsmart.vendor.entity;

import com.buildsmart.vendor.enums.ContractStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contract")
public class Contract {

    @Id
    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    @Column(name = "task_id")
    private String taskId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public Contract() {
    }

    public Contract(String contractId, String vendorId, String projectId, LocalDate startDate, LocalDate endDate, BigDecimal value, ContractStatus status, String taskId, String description) {
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
