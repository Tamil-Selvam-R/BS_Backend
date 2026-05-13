package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.dto.TemplateResponse;
import com.buildsmart.projectmanager.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Project Template Management APIs")
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    @Operation(summary = "Get all active templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<List<TemplateResponse>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllActiveTemplates());
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Get template by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<TemplateResponse> getTemplateById(@PathVariable String templateId) {
        return ResponseEntity.ok(templateService.getTemplateById(templateId));
    }
}
