package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.enums.InvoiceStatus;
import com.buildsmart.vendor.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    List<Invoice> findByContractId(String contractId);
    List<Invoice> findByStatus(InvoiceStatus status);
    Optional<Invoice> findTopByOrderByInvoiceIdDesc();
    Optional<Invoice> findByApprovalId(String approvalId);
    Optional<Invoice> findTopByApprovalIdStartingWithOrderByApprovalIdDesc(String prefix);

    /**
     * Paginated invoices for a set of contractIds.
     * Used when VENDOR role calls GET /api/invoices — vendor sees only invoices
     * belonging to their own contracts.
     */
    Page<Invoice> findByContractIdIn(List<String> contractIds, Pageable pageable);

    /**
     * All invoices for a set of contractIds (non-paginated).
     * Used for ownership checks on list endpoints.
     */
    List<Invoice> findByContractIdIn(List<String> contractIds);

    /**
     * Invoices for a set of contractIds filtered by status.
     * Used when VENDOR role calls GET /api/invoices/status/{status}.
     */
    List<Invoice> findByContractIdInAndStatus(List<String> contractIds, InvoiceStatus status);
}
