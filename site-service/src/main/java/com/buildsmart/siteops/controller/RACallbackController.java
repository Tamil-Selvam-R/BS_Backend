package com.buildsmart.siteops.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Internal callback endpoint — called by Resource & Allocation service when a
 * resource is allocated to a project.
 *
 * Path: /internal/resource-allocated
 * Security: /internal/** is permitted without authentication in SiteOps SecurityConfig.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
public class RACallbackController {

    @PostMapping("/resource-allocated")
    public ResponseEntity<Map<String, String>> receiveResourceAllocated(
            @RequestBody ResourceAllocatedRequest payload) {

        log.info("Received resource-allocated event from R&A: allocationId={}, projectId={}, resourceId={}",
                payload.allocationId(), payload.projectId(), payload.resourceId());

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "allocationId", payload.allocationId() != null ? payload.allocationId() : "",
                "projectId", payload.projectId() != null ? payload.projectId() : ""));
    }

    public record ResourceAllocatedRequest(
            String allocationId,
            String projectId,
            String resourceId,
            String resourceType,
            LocalDate assignedDate,
            LocalDate releasedDate,
            String allocatedBy
    ) {}
}
