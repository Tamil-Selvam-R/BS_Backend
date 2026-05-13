package com.buildsmart.vendor.service;

import com.buildsmart.vendor.dto.response.InvoiceResponse;
import com.buildsmart.vendor.dto.request.InvoiceRequest;
import com.buildsmart.vendor.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface InvoiceService {
    Page<InvoiceResponse> getAllInvoices(Pageable pageable);
    InvoiceResponse getInvoiceById(String id);
    List<InvoiceResponse> getInvoicesByContractId(String contractId);
    List<InvoiceResponse> getInvoicesByStatus(InvoiceStatus status);
    InvoiceResponse createInvoice(InvoiceRequest request, String submittedBy);
    InvoiceResponse updateInvoice(String id, InvoiceRequest request);
    void deleteInvoice(String id);
    InvoiceResponse submitInvoice(String id, String submittedBy, String authorization);
    InvoiceStatus getInvoiceStatus(String id);
    // Internal methods called by project manager via feign client
    InvoiceResponse updateInvoiceApprovalStatus(String id, String approvedBy);
    InvoiceResponse updateInvoiceRejectionStatus(String id, String rejectedBy, String rejectionReason);
    
    InvoiceResponse updateInvoiceApprovalStatusByApprovalId(String approvalId, String approvedBy);
    InvoiceResponse updateInvoiceRejectionStatusByApprovalId(String approvalId, String rejectedBy, String rejectionReason);
}
