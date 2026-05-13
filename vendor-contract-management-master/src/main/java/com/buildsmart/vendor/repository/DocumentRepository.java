package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.enums.DocumentStatus;
import com.buildsmart.vendor.enums.DocumentType;
import com.buildsmart.vendor.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByVendorId(String vendorId);
    List<Document> findByDocumentType(DocumentType documentType);
    List<Document> findByVendorIdAndDocumentType(String vendorId, DocumentType documentType);
    List<Document> findByStatus(DocumentStatus status);
    List<Document> findByProjectId(String projectId);
    Optional<Document> findTopByOrderByDocumentIdDesc();
    Optional<Document> findByApprovalId(String approvalId);
    Optional<Document> findTopByApprovalIdStartingWithOrderByApprovalIdDesc(String prefix);
}

