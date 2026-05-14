package com.buildsmart.safety.web.controller;

import com.buildsmart.safety.domain.model.IncidentSeverity;
import com.buildsmart.safety.domain.model.IncidentStatus;
import com.buildsmart.safety.security.JwtUtil;
import com.buildsmart.safety.service.IncidentService;
import com.buildsmart.safety.web.dto.IncidentDtos.CreateIncidentRequest;
import com.buildsmart.safety.web.dto.IncidentDtos.IncidentResponse;
import com.buildsmart.safety.web.dto.SafetyPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Incident REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - SAFETY_OFFICER: sees only incidents they reported (reportedBy = userId from JWT).
 *   - ADMIN: unrestricted access to all incidents.
 */
@RestController
@RequestMapping("/api/safety/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Safety incident management")
public class IncidentController {

    private final IncidentService incidentService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Report a new safety incident")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident by ID",
               description = "**SAFETY_OFFICER**: returns 403 if the incident was not reported by the calling officer.\n\n" +
                       "**ADMIN**: unrestricted.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentResponse> get(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        IncidentResponse incident = incidentService.get(id);
        if (isSafetyOfficer()) {
            String currentUserId = resolveUserIdFromToken(authorization);
            if (!currentUserId.equals(incident.reportedBy())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: you can only view incidents that you reported.");
            }
        }
        return ResponseEntity.ok(incident);
    }

    @GetMapping
    @Operation(summary = "Search / list incidents",
               description = "**SAFETY_OFFICER**: returns only incidents reported by the calling officer.\n\n" +
                       "**ADMIN**: returns all incidents matching the filters.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SafetyPageResponse<IncidentResponse>> search(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String authorization) {

        Optional<String> reportedByFilter = Optional.empty();
        if (isSafetyOfficer()) {
            reportedByFilter = Optional.of(resolveUserIdFromToken(authorization));
        }

        Page<IncidentResponse> result = incidentService.search(
                Optional.ofNullable(projectId), Optional.ofNullable(status), Optional.ofNullable(severity),
                Optional.ofNullable(dateFrom), Optional.ofNullable(dateTo),
                reportedByFilter,
                PageRequest.of(page, size, Sort.by("date").descending()));

        return ResponseEntity.ok(new SafetyPageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update incident status")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<IncidentResponse> updateStatus(
            @PathVariable String id, @RequestParam IncidentStatus status) {
        return ResponseEntity.ok(incidentService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an OPEN incident")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        incidentService.delete(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** True when the current JWT holder has the SAFETY_OFFICER role. */
    private boolean isSafetyOfficer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SAFETY_OFFICER"));
    }

    /**
     * Extracts the userId from the JWT token in the Authorization header.
     * Throws 401 if the token does not carry a userId claim.
     */
    private String resolveUserIdFromToken(String authorization) {
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring(7) : authorization;
        String userId = jwtUtil.extractUserId(token);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Cannot resolve userId from JWT. Please re-login.");
        }
        return userId;
    }
}
