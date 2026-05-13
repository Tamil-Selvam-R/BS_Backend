package com.buildsmart.finance.validator;

import com.buildsmart.finance.dto.request.PaymentCreateRequest;
import com.buildsmart.finance.entity.Expense;
import com.buildsmart.finance.entity.Payment;
import com.buildsmart.finance.exception.BusinessRuleException;
import com.buildsmart.finance.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentValidator {

    private final PaymentRepository paymentRepository;

    /**
     * Validate payment creation.
     * Amount is NOT taken from the request — it comes from the invoice fetched via VendorClient.
     *
     * @param request      the payment request (invoiceId, expenseId, paymentMethod)
     * @param expense      the linked expense (must be APPROVED)
     * @param invoiceAmount the amount fetched from the vendor invoice (used for expense ceiling check)
     */
    public void validatePaymentCreation(PaymentCreateRequest request, Expense expense, BigDecimal invoiceAmount) {
        log.info("Validating payment creation request for invoice: {}", request.getInvoiceId());

        // Expense must be APPROVED before a payment can be made against it
        if (!expense.getStatus().name().equals("APPROVED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-008",
                    "Expense must be APPROVED before payment can be processed"
            );
        }

        // Invoice amount must not exceed the approved expense amount
        if (invoiceAmount != null && invoiceAmount.compareTo(expense.getAmount()) > 0) {
            throw new BusinessRuleException(
                    "FIN-BUS-009",
                    "Invoice amount (" + invoiceAmount + ") exceeds the approved expense amount (" + expense.getAmount() + ")"
            );
        }
    }

    /**
     * Prevent double-payment — any active (non-rejected) payment for the same
     * invoice blocks a new one, regardless of amount.
     */
    public void checkDuplicatePayment(String invoiceId) {
        paymentRepository.findActivePaymentByInvoiceId(invoiceId).ifPresent(existing -> {
            throw new BusinessRuleException(
                    "FIN-BUS-010",
                    "An active payment already exists for invoice '" + invoiceId
                    + "'. Payment ID: " + existing.getPaymentId()
                    + ", Status: " + existing.getStatus().getDisplayName()
                    + ". Cancel or reject the existing payment before creating a new one."
            );
        });
    }

    /**
     * Validate payment can be rejected
     */
    public void validatePaymentCanBeRejected(String status) {
        if (status.equals("COMPLETED") || status.equals("REJECTED")) {
            throw new BusinessRuleException(
                    "FIN-BUS-011",
                    "Payment in " + status + " status cannot be rejected"
            );
        }
    }
}
