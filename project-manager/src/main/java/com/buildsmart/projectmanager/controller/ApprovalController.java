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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


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
    @Operation(summary = "Get all approvals")
    public ResponseEntity<List<ApprovalResponse>> getAllApprovals() {
        List<ApprovalResponse> responses = approvalService.getAllApprovals().stream()
                .map(ApprovalResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals")
    public ResponseEntity<List<ApprovalResponse>> getPendingApprovals() {
        List<ApprovalResponse> responses = approvalService.getPendingApprovals().stream()
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
}
