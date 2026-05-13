package com.buildsmart.projectmanager.controller;

import com.buildsmart.projectmanager.feign.VendorServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PM-side endpoints for reviewing and approving / rejecting vendor invoices.
 *
 * Flow:
 *  1. Vendor submits invoice → status becomes SUBMITTED in vendor service
 *  2. PM calls GET /api/vendor/invoices/pending to see submitted invoices
 *  3. PM calls POST /api/vendor/invoices/{approvalId}/approve or /reject
 *     → delegates to vendor-service via Feign (PUT /api/vendor-integration/approvals/{approvalId}/status)
 *     → vendor service updates invoice status and notifies vendor
 */
@RestController
@RequestMapping("/vendor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vendor Approval (PM)", description = "PM review and approval of vendor invoices")
public class VendorApprovalController {

    private final VendorServiceClient vendorServiceClient;

    /**
     * GET /api/vendor/invoices/pending
     * Returns all vendor invoices that are in SUBMITTED status — ready for PM review.
     */
    @GetMapping("/invoices/pending")
    @Operation(summary = "Get vendor invoices awaiting PM approval",
               description = "Fetches invoices with status SUBMITTED from the vendor service.")
    public ResponseEntity<List<VendorServiceClient.InvoiceDto>> getPendingInvoices() {
        List<VendorServiceClient.InvoiceDto> invoices = vendorServiceClient.getInvoicesByStatus("SUBMITTED");
        return ResponseEntity.ok(invoices);
    }

    /**
     * POST /api/vendor/invoices/{approvalId}/approve
     * PM approves a vendor invoice. The vendor service updates the invoice status to APPROVED
     * and notifies the vendor.
     */
    @PostMapping("/invoices/{approvalId}/approve")
    @Operation(summary = "Approve a vendor invoice",
               description = "PM approves the invoice identified by approvalId. Vendor is notified.")
    public ResponseEntity<String> approveInvoice(@PathVariable String approvalId) {
        String pmUserId = resolvePmUserId();
        ResponseEntity<String> response = vendorServiceClient.updateApprovalStatus(
                approvalId, "APPROVED", pmUserId, pmUserId, null);
        log.info("PM '{}' approved vendor invoice/document with approvalId '{}'", pmUserId, approvalId);
        return ResponseEntity.ok("Invoice " + approvalId + " approved successfully.");
    }

    /**
     * POST /api/vendor/invoices/{approvalId}/reject
     * PM rejects a vendor invoice. rejectionReason is MANDATORY.
     * Vendor service updates status to REJECTED and notifies vendor with the reason.
     *
     * Request body: { "rejectionReason": "Invoice amount exceeds contract value" }
     */
    @PostMapping("/invoices/{approvalId}/reject")
    @Operation(summary = "Reject a vendor invoice",
               description = "PM rejects the invoice. rejectionReason is mandatory.")
    public ResponseEntity<String> rejectInvoice(
            @PathVariable String approvalId,
            @RequestBody Map<String, String> body) {

        String rejectionReason = body.get("rejectionReason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("rejectionReason is mandatory when rejecting an invoice.");
        }

        String pmUserId = resolvePmUserId();
        vendorServiceClient.updateApprovalStatus(
                approvalId, "REJECTED", pmUserId, pmUserId, rejectionReason);
        log.info("PM '{}' rejected vendor invoice/document '{}'. Reason: {}", pmUserId, approvalId, rejectionReason);
        return ResponseEntity.ok("Invoice " + approvalId + " rejected. Vendor notified with reason.");
    }

    private String resolvePmUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "unknown-pm";
    }
}
