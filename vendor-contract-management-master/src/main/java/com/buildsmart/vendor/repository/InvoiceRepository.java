package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.enums.InvoiceStatus;
import com.buildsmart.vendor.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    List<Invoice> findByContractId(String contractId);
    List<Invoice> findByStatus(InvoiceStatus status);
    Optional<Invoice> findTopByOrderByInvoiceIdDesc();
    Optional<Invoice> findByApprovalId(String approvalId);
    Optional<Invoice> findTopByApprovalIdStartingWithOrderByApprovalIdDesc(String prefix);
}
