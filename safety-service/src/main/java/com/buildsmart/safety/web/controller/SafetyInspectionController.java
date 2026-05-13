package com.buildsmart.safety.web.controller;

import com.buildsmart.safety.domain.model.InspectionStatus;
import com.buildsmart.safety.domain.model.InspectionType;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/safety/inspections")
@RequiredArgsConstructor
@Tag(name = "Inspections", description = "Safety inspection management")
public class SafetyInspectionController {

    private final SafetyInspectionService inspectionService;

    @PostMapping
    @Operation(summary = "Schedule a new inspection (SAFETY_OFFICER / ADMIN only)")
    @PreAuthorize("hasAnyRole('SAFETY_OFFICER','ADMIN')")
    public ResponseEntity<InspectionResponse> create(@Valid @RequestBody CreateInspectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get inspection by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InspectionResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(inspectionService.get(id));
    }

    @GetMapping
    @Operation(summary = "Search / list inspections")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SafetyPageResponse<InspectionResponse>> search(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<InspectionResponse> result = inspectionService.search(
                Optional.ofNullable(projectId), Optional.ofNullable(status),
                Optional.ofNullable(dateFrom), Optional.ofNullable(dateTo),
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
}
