package com.buildsmart.safety.web.controller;

import com.buildsmart.safety.domain.model.IncidentSeverity;
import com.buildsmart.safety.domain.model.IncidentStatus;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/safety/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Safety incident management")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    @Operation(summary = "Report a new safety incident")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(incidentService.get(id));
    }

    @GetMapping
    @Operation(summary = "Search / list incidents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SafetyPageResponse<IncidentResponse>> search(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<IncidentResponse> result = incidentService.search(
                Optional.ofNullable(projectId), Optional.ofNullable(status), Optional.ofNullable(severity),
                Optional.ofNullable(dateFrom), Optional.ofNullable(dateTo),
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
}
