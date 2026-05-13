package com.buildsmart.finance.dto.request;

import com.buildsmart.finance.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCreateRequest {

    @NotBlank(message = "Invoice ID is required")
    private String invoiceId;

    @NotBlank(message = "Expense ID is required")
    private String expenseId;

    // Amount is NOT entered by Finance — it is fetched automatically from the invoice.
    // Only APPROVED invoices are accepted; the amount is taken directly from the invoice record.

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String bankReferenceNumber;
}
