package com.buildsmart.siteops.service;

import com.buildsmart.siteops.dto.PaginatedResponse;
import com.buildsmart.siteops.dto.SiteLogRequest;
import com.buildsmart.siteops.dto.SiteLogPhotoUploadResponse;
import com.buildsmart.siteops.dto.SiteLogResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface SiteLogService {

    SiteLogPhotoUploadResponse uploadSiteLogPhoto(String logId, MultipartFile photo, String uploadedBy);

    SiteLogResponse createSiteLog(SiteLogRequest request, String submittedBy, String authorization);

    SiteLogResponse getSiteLogById(String logId);

    List<SiteLogResponse> getSiteLogsByProject(String projectId);

    List<SiteLogResponse> getSiteLogsByProjectAndDateRange(String projectId, LocalDate from, LocalDate to);

    SiteLogResponse getSiteLogByProjectAndDate(String projectId, LocalDate date);

    SiteLogResponse getLatestSiteLog(String projectId);

    PaginatedResponse<SiteLogResponse> getSiteLogsByProjectPaginated(
            String projectId, int pageNumber, int pageSize, String sortBy, String sortDirection);

    PaginatedResponse<SiteLogResponse> getSiteLogsByProjectAndDateRangePaginated(
            String projectId, LocalDate from, LocalDate to, int pageNumber, int pageSize, String sortBy, String sortDirection);

    /**
     * Submits a previously created site log to the Project Manager for approval.
     *
     * Flow:
     *  - Validates the log exists, belongs to the caller (or caller is ADMIN/PM)
     *    and is currently PENDING or REJECTED (so re-submission after rejection works).
     *  - Sets reviewStatus to SUBMITTED locally.
     *  - Creates a formal ApprovalRequest in PM via Feign so PM sees the entry
     *    at GET /api/approvals.
     *  - Notifies the SE locally that the submission was accepted.
     *
     * PM's approve/reject decision arrives via the existing
     * /api/siteops/tasks/internal/approval-result callback (extended to also
     * flip the SiteLog row when it matches the approvalId).
     */
    SiteLogResponse submitSiteLog(String logId, String submittedBy, String authorization);
}
