package com.buildsmart.analytics.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mirror of vendor module's InvoiceResponse (com.buildsmart.vendor.dto.response.InvoiceResponse).
 * Used by report-analytics to compute real spend + invoice approval rate.
 *
 * Status values come from vendor module's InvoiceStatus enum:
 *   PENDING, SUBMITTED, APPROVED, REJECTED, PAID, OVERDUE, CANCELLED.
 * Field names + JSON shape match the vendor response so Jackson can deserialize directly.
 */
public record VendorInvoiceDTO(
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
        LocalDateTime submittedOn,
        LocalDateTime approvedOn,
        LocalDateTime rejectedOn
) {}
