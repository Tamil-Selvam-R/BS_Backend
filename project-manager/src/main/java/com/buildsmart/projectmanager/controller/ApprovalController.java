package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.dto.ApprovalResponse;
import com.buildsmart.projectmanager.dto.CreateApprovalRequest;
import com.buildsmart.projectmanager.entity.ApprovalRequest;
import com.buildsmart.projectmanager.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Approval REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - PROJECT_MANAGER: sees only approvals for projects they created (project.createdBy = caller's userId).
 *   - ADMIN: unrestricted access to all approvals.
 *
 * PM JWT filter resolves userId via IAM Feign call and sets it as the Security principal,
 * so {@code auth.getName()} reliably returns the caller's userId.
 */
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Approval Workflow APIs")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @Operation(summary = "Create approval request")
    public ResponseEntity<ApprovalResponse> createApprovalRequest(
            @Valid @RequestBody CreateApprovalRequest request) {
        ApprovalRequest approval = approvalService.createApprovalRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApprovalResponse.fromEntity(approval));
    }

    @GetMapping
    @Operation(summary = "Get all approvals",
               description = "**PROJECT_MANAGER**: returns only approvals for projects created by the calling user.\n\n" +
                       "**ADMIN**: returns all approvals.")
    public ResponseEntity<List<ApprovalResponse>> getAllApprovals() {
        List<ApprovalRequest> approvals;
        if (isAdmin()) {
            approvals = approvalService.getAllApprovals();
        } else {
            String currentUserId = resolveCurrentUserId();
            approvals = approvalService.getApprovalsByProjectOwner(currentUserId);
        }
        List<ApprovalResponse> responses = approvals.stream()
                .map(ApprovalResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals",
               description = "**PROJECT_MANAGER**: returns only pending approvals for projects created by the calling user.\n\n" +
                       "**ADMIN**: returns all pending approvals.")
    public ResponseEntity<List<ApprovalResponse>> getPendingApprovals() {
        List<ApprovalRequest> approvals;
        if (isAdmin()) {
            approvals = approvalService.getPendingApprovals();
        } else {
            String currentUserId = resolveCurrentUserId();
            approvals = approvalService.getPendingApprovalsByProjectOwner(currentUserId);
        }
        List<ApprovalResponse> responses = approvals.stream()
                .map(ApprovalResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get approvals by project")
    public ResponseEntity<List<ApprovalResponse>> getApprovalsByProject(@PathVariable String projectId) {
        List<ApprovalResponse> responses = approvalService.getApprovalsByProject(projectId).stream()
                .map(ApprovalResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{approvalId}/approve")
    @Operation(summary = "Approve request")
    public ResponseEntity<ApprovalResponse> approveRequest(@PathVariable String approvalId) {
        return ResponseEntity.ok(ApprovalResponse.fromEntity(approvalService.approveRequest(approvalId)));
    }

    @PostMapping("/{approvalId}/reject")
    @Operation(summary = "Reject request - Rejection reason is MANDATORY")
    public ResponseEntity<ApprovalResponse> rejectRequest(
            @PathVariable String approvalId,
            @RequestParam String rejectionReason) {
        return ResponseEntity.ok(ApprovalResponse.fromEntity(approvalService.rejectRequest(approvalId, rejectionReason)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get approval statistics")
    public ResponseEntity<ApprovalService.ApprovalStats> getApprovalStats() {
        return ResponseEntity.ok(approvalService.getApprovalStats());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the current user's userId from the Security context. */
    private String resolveCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    /** True when the current JWT holder has the ADMIN role. */
    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
