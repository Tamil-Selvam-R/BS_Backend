package com.buildsmart.siteops.controller;

import com.buildsmart.siteops.dto.PaginatedResponse;
import com.buildsmart.siteops.dto.SiteLogRequest;
import com.buildsmart.siteops.dto.SiteLogPhotoUploadResponse;
import com.buildsmart.siteops.dto.SiteLogResponse;
import com.buildsmart.siteops.security.AuthenticationHelper;
import com.buildsmart.siteops.service.SiteLogService;
import com.buildsmart.siteops.util.PaginationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping({"/api/sitelogs", "/api/siteops/sitelogs"})
@RequiredArgsConstructor
public class SiteLogController {

    @Value("${app.upload.dir:uploads/sitelogs/}")
    private String uploadDir;

    private static final String DEFAULT_SITELOG_SORT = "logDate";
    private static final Set<String> ALLOWED_SITELOG_SORT_FIELDS = Set.of(
            "logDate", "submittedAt", "progressPercent", "projectId", "logId"
    );

    private final SiteLogService siteLogService;
    private final AuthenticationHelper authenticationHelper;
    private final Environment environment;

    /**
     * POST /api/sitelogs/{logId}/photo-upload
     * Attach JPEG photo proof to an already-created site log.
     */
    @PostMapping(value = "/{logId}/photo-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SiteLogPhotoUploadResponse> uploadPhoto(
            @PathVariable String logId,
            @RequestPart("photo") MultipartFile photo,
            @RequestHeader("Authorization") String authorization) {

        String uploadedBy = authenticationHelper.extractUserIdFromHeader(authorization);
        SiteLogPhotoUploadResponse response = siteLogService.uploadSiteLogPhoto(logId, photo, uploadedBy);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/sitelogs/{logId}/photo
     * Serves the JPEG photo attached to a site log.
     * Accessible by PROJECT_MANAGER to review site progress photos.
     */
    @GetMapping(value = "/{logId}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> getPhoto(@PathVariable String logId) {
        SiteLogResponse log = siteLogService.getSiteLogById(logId);
        if (log.photoUrl() == null || log.photoUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path filePath = Paths.get(uploadDir).resolve(log.photoUrl()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header("Content-Disposition", "inline; filename=\"" + log.photoUrl() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/sitelogs
     * Create daily site log first; photo can be attached later via /{logId}/photo-upload.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SiteLogResponse> create(
            @Valid @RequestBody SiteLogRequest request,
            @RequestHeader("Authorization") String authorization) {

        // Extract submittedBy directly from JWT token (with module validation)
        String submittedBy = authenticationHelper.extractUserIdFromHeader(authorization);

         SiteLogResponse created = siteLogService.createSiteLog(request, submittedBy, authorization);
        return ResponseEntity
                .created(URI.create("/api/sitelogs/" + created.logId()))
                .body(created);
    }

    @GetMapping("/{logId}")
    public ResponseEntity<SiteLogResponse> getById(@PathVariable String logId) {

        return ResponseEntity.ok(siteLogService.getSiteLogById(logId));
    }

    /**
     * POST /api/sitelogs/{logId}/submit
     *
     * Sends a previously created daily site log to the Project Manager for approval.
     * Local reviewStatus moves PENDING (or REJECTED, for resubmission) → SUBMITTED.
     * PM sees the entry at GET /api/approvals; PM's approve/reject decision flips
     * this row to APPROVED / REJECTED via the existing approval-result callback
     * with notifications firing back to the SE in both cases.
     *
     * Mirrors the vendor invoice / safety task / finance task submit pattern.
     */
    @PostMapping("/{logId}/submit")
    public ResponseEntity<SiteLogResponse> submit(
            @PathVariable String logId,
            @RequestHeader("Authorization") String authorization) {
        String submittedBy = authenticationHelper.extractUserIdFromHeader(authorization);
        SiteLogResponse response = siteLogService.submitSiteLog(logId, submittedBy, authorization);
        return ResponseEntity.ok(response);
    }


    @GetMapping
    public ResponseEntity<List<SiteLogResponse>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<SiteLogResponse> result = (from != null && to != null)
                ? siteLogService.getSiteLogsByProjectAndDateRange(projectId, from, to)
                : siteLogService.getSiteLogsByProject(projectId);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sitelogs/paginated
     * Get paginated site logs for a project.
     * Query params: projectId (required), pageNumber (default 0), pageSize (default 10),
     * sortBy (default logDate), sortDirection (default DESC)
     */
    @GetMapping("/paginated/list")
    public ResponseEntity<PaginatedResponse<SiteLogResponse>> listPaginated(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "logDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        String safeSortBy = PaginationUtil.normalizeSortBy(sortBy, ALLOWED_SITELOG_SORT_FIELDS, DEFAULT_SITELOG_SORT);

        PaginatedResponse<SiteLogResponse> result =
                siteLogService.getSiteLogsByProjectPaginated(projectId, pageNumber, pageSize, safeSortBy, sortDirection);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sitelogs/paginated/date-range
     * Get paginated site logs for a project within a date range.
     */
    @GetMapping("/paginated/date-range")
    public ResponseEntity<PaginatedResponse<SiteLogResponse>> listByDateRangePaginated(
            @RequestParam String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "logDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        String safeSortBy = PaginationUtil.normalizeSortBy(sortBy, ALLOWED_SITELOG_SORT_FIELDS, DEFAULT_SITELOG_SORT);

        PaginatedResponse<SiteLogResponse> result =
                siteLogService.getSiteLogsByProjectAndDateRangePaginated(
                        projectId, from, to, pageNumber, pageSize, safeSortBy, sortDirection);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-date")
    public ResponseEntity<SiteLogResponse> byDate(
            @RequestParam String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(siteLogService.getSiteLogByProjectAndDate(projectId, date));
    }

    @GetMapping("/latest/{projectId}")
    public ResponseEntity<SiteLogResponse> latest(@PathVariable String projectId) {
        return ResponseEntity.ok(siteLogService.getLatestSiteLog(projectId));
    }

    /**
     * GET /api/sitelogs/instance-info
     * Returns which server instance handled this request.
     * Use this to visually verify load balancing is working — run two instances
     * and call this endpoint repeatedly; the port should alternate.
     */
    @GetMapping("/instance-info")
    public ResponseEntity<Map<String, Object>> instanceInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service",   "siteops-service");
        info.put("port",      environment.getProperty("server.port"));
        info.put("message",   "This request was handled by instance on port " + environment.getProperty("server.port"));
        info.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(info);
    }
}
