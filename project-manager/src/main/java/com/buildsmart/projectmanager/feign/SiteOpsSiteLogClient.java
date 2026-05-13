package com.buildsmart.projectmanager.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PM-side Feign client for SiteOps daily site log endpoints.
 *
 * Used by {@code POST /api/projects/{projectId}/milestones/progress} to
 * pull the project's latest cumulative {@code progressPercent} directly
 * from SiteOps when the caller does not supply it in the request body.
 *
 * <p>Eureka service name: {@code siteops-service}. PM forwards the caller's
 * Authorization header so SiteOps's JWT filter accepts the request — the
 * underlying endpoint allows the PROJECT_MANAGER role.</p>
 *
 * <p>A separate {@code contextId} is required so this client does not collide
 * with {@link SiteOpsClient}, which has {@code path = "/api/issues"} baked in.</p>
 */
@FeignClient(
        name = "siteops-service",
        contextId = "pmSiteOpsSiteLogClient"
)
public interface SiteOpsSiteLogClient {

    /**
     * Fetches the most recent daily site log for the given project.
     * SiteOps endpoint: GET /api/sitelogs/latest/{projectId}
     */
    @GetMapping("/api/sitelogs/latest/{projectId}")
    SiteLogDto getLatestSiteLog(
            @PathVariable("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);

    /**
     * Fetches all daily site logs for the given project.
     * SiteOps endpoint: GET /api/sitelogs?projectId={projectId}
     * PM uses this to see all site logs including those with photos.
     */
    @GetMapping("/api/sitelogs")
    List<SiteLogDto> getSiteLogsByProject(
            @RequestParam("projectId") String projectId,
            @RequestHeader("Authorization") String authorization);

    /**
     * Fetches the JPEG photo bytes attached to a site log.
     * SiteOps endpoint: GET /api/sitelogs/{logId}/photo
     */
    @GetMapping(value = "/api/sitelogs/{logId}/photo")
    ResponseEntity<byte[]> getSiteLogPhoto(
            @PathVariable("logId") String logId,
            @RequestHeader("Authorization") String authorization);

    /**
     * Minimal mirror of SiteOps's {@code SiteLogResponse} record. Only the
     * fields PM actually needs are declared; unknown JSON keys are ignored
     * so additions on the SiteOps side do not break this client.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteLogDto(
            String logId,
            String projectId,
            LocalDate logDate,
            String activities,
            String issuesSummary,
            BigDecimal progressPercent,
            String submittedBy,
            LocalDateTime submittedAt,
            String reviewStatus,
            String reviewedBy,
            LocalDateTime reviewedAt,
            String reviewerComments,
            String photoUrl,
            String approvalId
    ) {}
}
