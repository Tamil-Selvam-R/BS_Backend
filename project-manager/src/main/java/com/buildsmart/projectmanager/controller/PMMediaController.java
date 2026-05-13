package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.feign.SiteOpsSiteLogClient;
import com.buildsmart.projectmanager.feign.VendorServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PM-side unified media viewer — all queries are scoped to a projectId.
 *
 * Site engineer photos:
 *   GET /api/pm/media/projects/{projectId}/sitelogs           — list logs (with photoUrl)
 *   GET /api/pm/media/projects/{projectId}/sitelogs/{logId}/photo — view the JPG
 *
 * Vendor documents:
 *   GET /api/pm/media/projects/{projectId}/documents          — list all vendor docs for project
 *   GET /api/pm/media/projects/{projectId}/documents/{id}/download — download the file
 *
 * No files are stored here — this controller is a pure proxy.
 */
@RestController
@RequestMapping("/api/pm/media/projects/{projectId}")
@PreAuthorize("hasRole('PROJECT_MANAGER')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PM Media Viewer", description = "Project Manager access to site engineer photos and vendor documents — all scoped by projectId")
public class PMMediaController {

    private final SiteOpsSiteLogClient siteOpsSiteLogClient;
    private final VendorServiceClient vendorServiceClient;

    // ─────────────────────────────────────────────────────────────────────────
    // SITE ENGINEER PHOTOS
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "List all site logs with photos for a project",
            description = "Returns all daily site logs for the project. " +
                    "Each entry has a photoUrl field — non-null means a photo is attached. " +
                    "Use GET .../sitelogs/{logId}/photo to view the actual image.")
    @GetMapping("/sitelogs")
    public ResponseEntity<List<SiteOpsSiteLogClient.SiteLogDto>> getSiteLogsForProject(
            @PathVariable String projectId,
            @RequestHeader("Authorization") String authorization) {
        log.info("PM listing site logs for project: {}", projectId);
        List<SiteOpsSiteLogClient.SiteLogDto> logs = siteOpsSiteLogClient.getSiteLogsByProject(projectId, authorization);
        return ResponseEntity.ok(logs);
    }

    @Operation(
            summary = "View a site engineer's photo for a project",
            description = "Streams the JPEG image attached to the specified site log. " +
                    "Returns 404 if the log has no photo or if it belongs to a different project.")
    @GetMapping(value = "/sitelogs/{logId}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getSiteLogPhoto(
            @PathVariable String projectId,
            @PathVariable String logId,
            @RequestHeader("Authorization") String authorization) {
        log.info("PM viewing photo — project: {}, logId: {}", projectId, logId);
        try {
            // Fetch the log first to verify it belongs to this project
            List<SiteOpsSiteLogClient.SiteLogDto> logs = siteOpsSiteLogClient.getSiteLogsByProject(projectId, authorization);
            boolean belongsToProject = logs.stream()
                    .anyMatch(l -> logId.equals(l.logId()));
            if (!belongsToProject) {
                log.warn("Site log {} does not belong to project {}", logId, projectId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            ResponseEntity<byte[]> response = siteOpsSiteLogClient.getSiteLogPhoto(logId, authorization);
            if (response == null || response.getBody() == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"sitelog-" + logId + ".jpg\"")
                    .body(response.getBody());
        } catch (Exception e) {
            log.warn("Could not fetch photo for site log {} (project {}): {}", logId, projectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VENDOR DOCUMENTS
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "List all vendor documents for a project",
            description = "Returns all documents uploaded by vendors for this specific project. " +
                    "Use GET .../documents/{id}/download to download any of them.")
    @GetMapping("/documents")
    public ResponseEntity<List<VendorServiceClient.DocumentDto>> getVendorDocumentsForProject(
            @PathVariable String projectId,
            @RequestHeader("Authorization") String authorization) {
        log.info("PM listing vendor documents for project: {}", projectId);
        List<VendorServiceClient.DocumentDto> documents =
                vendorServiceClient.getVendorDocumentsByProjectId(projectId, authorization);
        return ResponseEntity.ok(documents);
    }

    @Operation(
            summary = "Download a vendor document for a project",
            description = "Downloads the file (PDF or other) uploaded by the vendor. " +
                    "Verifies the document belongs to the requested project before serving.")
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadVendorDocument(
            @PathVariable String projectId,
            @PathVariable String documentId,
            @RequestHeader("Authorization") String authorization) {
        log.info("PM downloading vendor document {} for project {}", documentId, projectId);
        try {
            // Verify document belongs to this project and capture metadata for the response headers
            List<VendorServiceClient.DocumentDto> docs =
                    vendorServiceClient.getVendorDocumentsByProjectId(projectId, authorization);
            VendorServiceClient.DocumentDto doc = docs.stream()
                    .filter(d -> documentId.equals(d.documentId()))
                    .findFirst()
                    .orElse(null);
            if (doc == null) {
                log.warn("Document {} does not belong to project {}", documentId, projectId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            ResponseEntity<byte[]> response = vendorServiceClient.downloadVendorDocument(documentId, authorization);
            if (response == null || response.getBody() == null) {
                return ResponseEntity.notFound().build();
            }

            // Use the original filename and content type stored at upload time
            String filename = (doc.documentName() != null && !doc.documentName().isBlank())
                    ? doc.documentName()
                    : documentId + ".pdf";
            String contentType = (doc.contentType() != null && !doc.contentType().isBlank())
                    ? doc.contentType()
                    : "application/pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(response.getBody());
        } catch (Exception e) {
            log.warn("Could not download vendor document {} (project {}): {}", documentId, projectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
