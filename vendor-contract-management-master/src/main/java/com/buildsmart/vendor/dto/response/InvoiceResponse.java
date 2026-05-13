package com.buildsmart.vendor.dto.response;

import com.buildsmart.vendor.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class InvoiceResponse {

        private String invoiceId;

        private String contractId;

        private String approvalId;

        private String taskId;

        private BigDecimal amount;

        private LocalDate date;

        private InvoiceStatus status;

        private String approvedBy;

        private String rejectedBy;

        private String rejectionReason;

        private String description;
        private String submittedBy;
        private LocalDateTime submittedOn;
        private LocalDateTime approvedOn;
        private LocalDateTime rejectedOn;

        public InvoiceResponse() {
        }

        public InvoiceResponse(String invoiceId, String contractId, BigDecimal amount, LocalDate date, InvoiceStatus status, String approvedBy, String rejectedBy, String rejectionReason, String description, String submittedBy, LocalDateTime submittedOn, LocalDateTime approvedOn, LocalDateTime rejectedOn) {
            this.invoiceId = invoiceId;
            this.contractId = contractId;
            this.amount = amount;
            this.date = date;
            this.status = status;
            this.approvedBy = approvedBy;
            this.rejectedBy = rejectedBy;
            this.rejectionReason = rejectionReason;
            this.description = description;
            this.submittedBy = submittedBy;
            this.submittedOn = submittedOn;
            this.approvedOn = approvedOn;
            this.rejectedOn = rejectedOn;
        }

        public String getInvoiceId() {
            return invoiceId;
        }

        public void setInvoiceId(String invoiceId) {
            this.invoiceId = invoiceId;
        }

        public String getContractId() {
            return contractId;
        }

        public void setContractId(String contractId) {
            this.contractId = contractId;
        }

        public String getApprovalId() {
            return approvalId;
        }

        public void setApprovalId(String approvalId) {
            this.approvalId = approvalId;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public InvoiceStatus getStatus() {
            return status;
        }

        public void setStatus(InvoiceStatus status) {
            this.status = status;
        }

        public String getApprovedBy() {
            return approvedBy;
        }

        public void setApprovedBy(String approvedBy) {
            this.approvedBy = approvedBy;
        }

        public String getRejectedBy() {
            return rejectedBy;
        }

        public void setRejectedBy(String rejectedBy) {
            this.rejectedBy = rejectedBy;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSubmittedBy() {
            return submittedBy;
        }

        public void setSubmittedBy(String submittedBy) {
            this.submittedBy = submittedBy;
        }

        public LocalDateTime getSubmittedOn() {
            return submittedOn;
        }

        public void setSubmittedOn(LocalDateTime submittedOn) {
            this.submittedOn = submittedOn;
        }

        public LocalDateTime getApprovedOn() {
            return approvedOn;
        }

        public void setApprovedOn(LocalDateTime approvedOn) {
            this.approvedOn = approvedOn;
        }

        public LocalDateTime getRejectedOn() {
            return rejectedOn;
        }

        public void setRejectedOn(LocalDateTime rejectedOn) {
            this.rejectedOn = rejectedOn;
        }
}

