package com.buildsmart.finance.dto.response;

import com.buildsmart.finance.entity.enums.PaymentMethod;
import com.buildsmart.finance.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String paymentId;
    private String invoiceId;
    private String expenseId;
    private String budgetId;
    private String vendorId;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String bankReferenceNumber;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
