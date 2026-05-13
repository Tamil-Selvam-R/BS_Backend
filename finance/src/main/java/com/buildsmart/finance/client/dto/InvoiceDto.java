package com.buildsmart.finance.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps the InvoiceResponse returned by the vendor-service.
 *
 * vendorId is NOT present in the vendor's response — the vendor service stores
 * the vendor's display name in submittedBy. Use submittedBy as the vendor
 * identifier for notifications; vendorId will be null unless the vendor service
 * adds it in future.
 */
public record InvoiceDto(
        String invoiceId,
        String contractId,
        String approvalId,
        String taskId,
        BigDecimal amount,
        LocalDate date,
        String status,
        String approvedBy,
        String rejectedBy,
        String rejectionReason,
        String description,
        String submittedBy,
        // vendorId is not returned by vendor-service; kept for backward compat — always null
        String vendorId
) {}
