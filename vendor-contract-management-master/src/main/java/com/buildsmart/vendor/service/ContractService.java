package com.buildsmart.vendor.service;

import com.buildsmart.vendor.dto.response.ContractResponse;
import com.buildsmart.vendor.dto.request.ContractRequest;
import com.buildsmart.vendor.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ContractService {
    Page<ContractResponse> getAllContracts(Pageable pageable);
    ContractResponse getContractById(String id);
    List<ContractResponse> getContractsByVendorId(String vendorId);
    List<ContractResponse> getContractsByStatus(ContractStatus status);
    ContractResponse createContract(ContractRequest request, String vendorId);
    ContractResponse updateContract(String id, ContractRequest request, String vendorId);
    void deleteContract(String id);

    /** Paginated contracts for a specific vendor — used when VENDOR role calls GET /api/contracts. */
    Page<ContractResponse> getContractsByVendorIdPaginated(String vendorId, Pageable pageable);

    /** Vendor's contracts filtered by status — used for GET /api/contracts/status/{status}. */
    List<ContractResponse> getContractsByVendorIdAndStatus(String vendorId, ContractStatus status);

    /**
     * Returns only the contractId strings for a vendor.
     * Used by InvoiceController and DeliveryController to check resource ownership:
     * if the resource's contractId is not in this list, the vendor gets 403.
     */
    List<String> getContractIdsByVendorId(String vendorId);
}
