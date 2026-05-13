package com.buildsmart.finance.entity;

import com.buildsmart.finance.entity.enums.PaymentMethod;
import com.buildsmart.finance.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"invoice_id", "amount"},
                name = "uk_invoice_amount")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @Column(name = "payment_id", length = 20)
    private String paymentId;

    @Column(name = "invoice_id", length = 20, nullable = false)
    private String invoiceId;

    @Column(name = "expense_id", length = 20, nullable = false)
    private String expenseId;

    @Column(name = "budget_id", length = 20)
    private String budgetId;

    @Column(name = "vendor_id", length = 100)
    private String vendorId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "bank_reference_number", length = 100)
    private String bankReferenceNumber;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;
}
