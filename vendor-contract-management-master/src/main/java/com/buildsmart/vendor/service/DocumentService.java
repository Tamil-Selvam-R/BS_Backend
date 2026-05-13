package com.buildsmart.vendor.service;

import com.buildsmart.vendor.dto.response.DocumentResponse;
import com.buildsmart.vendor.dto.request.DocumentRequest;
import com.buildsmart.vendor.enums.DocumentStatus;
import com.buildsmart.vendor.enums.DocumentType;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    Page<DocumentResponse> getAllDocuments(Pageable pageable);
    DocumentResponse getDocumentById(String id);
    List<DocumentResponse> getDocumentsByVendorId(String vendorId);
    List<DocumentResponse> getDocumentsByType(DocumentType documentType);
    List<DocumentResponse> getDocumentsByVendorIdAndType(String vendorId, DocumentType documentType);
    DocumentResponse uploadDocument(DocumentRequest request, MultipartFile file);
    DocumentResponse updateDocument(String id, DocumentRequest request, MultipartFile file);
    Resource downloadDocument(String id);
    void deleteDocument(String id);
    DocumentResponse submitDocument(String id, String submittedBy, String authorization);
    List<DocumentResponse> getDocumentsByStatus(DocumentStatus status);
    List<DocumentResponse> getDocumentsByProjectId(String projectId);
    DocumentStatus getDocumentStatus(String id);
    // Internal methods called by project manager via feign client
    DocumentResponse updateDocumentApprovalStatus(String id, String approvedBy);
    DocumentResponse updateDocumentRejectionStatus(String id, String rejectedBy, String rejectionReason);

    DocumentResponse updateDocumentApprovalStatusByApprovalId(String approvalId, String approvedBy);
    DocumentResponse updateDocumentRejectionStatusByApprovalId(String approvalId, String rejectedBy, String rejectionReason);
}
