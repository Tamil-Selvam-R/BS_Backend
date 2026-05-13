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
}
