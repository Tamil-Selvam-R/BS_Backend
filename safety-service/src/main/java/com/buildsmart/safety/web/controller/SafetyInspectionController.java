package com.buildsmart.safety.web.controller;

import com.buildsmart.safety.domain.model.InspectionStatus;
import com.buildsmart.safety.domain.model.InspectionType;
import com.buildsmart.safety.security.JwtUtil;
import com.buildsmart.safety.service.SafetyInspectionService;
import com.buildsmart.safety.web.dto.InspectionDtos.CreateInspectionRequest;
import com.buildsmart.safety.web.dto.InspectionDtos.InspectionResponse;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Safety Inspection REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - SAFETY_OFFICER: sees only inspections they conducted (officerId = userId from JWT).
 *   - ADMIN: unrestricted access to all inspections.
 */
@RestController
@RequestMapping("/api/safety/inspections")
@RequiredArgsConstructor
@Tag(name = "Inspections", description = "Safety inspection management")
public class SafetyInspectionController {

    private final SafetyInspectionService inspectionService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Schedule a new inspection (SAFETY_OFFICER / ADMIN only)")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<InspectionResponse> create(@Valid @RequestBody CreateInspectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get inspection by ID",
               description = "**SAFETY_OFFICER**: returns 403 if the inspection was not conducted by the calling officer.\n\n" +
                       "**ADMIN**: unrestricted.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InspectionResponse> get(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        InspectionResponse inspection = inspectionService.get(id);
        if (isSafetyOfficer()) {
            String currentUserId = resolveUserIdFromToken(authorization);
            if (!currentUserId.equals(inspection.officerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: you can only view inspections that you conducted.");
            }
        }
        return ResponseEntity.ok(inspection);
    }

    @GetMapping
    @Operation(summary = "Search / list inspections",
               description = "**SAFETY_OFFICER**: returns only inspections conducted by the calling officer.\n\n" +
                       "**ADMIN**: returns all inspections matching the filters.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SafetyPageResponse<InspectionResponse>> search(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String authorization) {

        Optional<String> officerIdFilter = Optional.empty();
        if (isSafetyOfficer()) {
            officerIdFilter = Optional.of(resolveUserIdFromToken(authorization));
        }

        Page<InspectionResponse> result = inspectionService.search(
                Optional.ofNullable(projectId), Optional.ofNullable(status),
                Optional.ofNullable(dateFrom), Optional.ofNullable(dateTo),
                officerIdFilter,
                PageRequest.of(page, size, Sort.by("date").descending()));

        return ResponseEntity.ok(new SafetyPageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update inspection status")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<InspectionResponse> updateStatus(
            @PathVariable String id, @RequestParam InspectionStatus status) {
        return ResponseEntity.ok(inspectionService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a SCHEDULED inspection")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        inspectionService.delete(id);
    }

    @GetMapping("/types")
    @Operation(summary = "Get all inspection types (dropdown)")
    public ResponseEntity<List<String>> getInspectionTypes() {
        return ResponseEntity.ok(Arrays.stream(InspectionType.values()).map(Enum::name).toList());
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
