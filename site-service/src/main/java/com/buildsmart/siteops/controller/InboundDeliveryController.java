package com.buildsmart.siteops.controller;

import com.buildsmart.siteops.client.NotificationServiceClient;
import com.buildsmart.siteops.client.VendorClient;
import com.buildsmart.siteops.client.dto.NotificationCreateRequest;
import com.buildsmart.siteops.entity.InboundDelivery;
import com.buildsmart.siteops.repository.InboundDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * FEATURE SET 2 — SiteOps endpoints for vendor delivery flow.
 *
 * Two responsibilities live here:
 *   1. INBOUND  (/internal/deliveries) — vendor pushes a new dispatch event.
 *      Permitted without auth via /internal/** in SiteOps SecurityConfig.
 *   2. CONFIRM  (/api/deliveries/{id}/confirm) — Site Officer marks the
 *      delivery RECEIVED or NOT_RECEIVED; we call Vendor back via Feign so
 *      the vendor's row reflects the physical reality.
 *
 * Note on central notifications: the Vendor module already pushes
 * DELIVERY_CREATED/DISPATCHED/CONFIRMED events to the central service with
 * the right toUserId (PM + vendor). This controller emits an ADDITIONAL
 * SITE-OPS-side central push when the Site Officer confirms — targeted
 * at the original reporter / site engineer — for their own bell icon.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InboundDeliveryController {

    private final InboundDeliveryRepository inboundDeliveryRepository;
    private final VendorClient vendorClient;
    private final NotificationServiceClient notificationServiceClient;

    /* ── 1. Vendor → Site dispatch receiver ─────────────────────────────── */

    @PostMapping("/internal/deliveries")
    public ResponseEntity<Map<String, String>> receiveDispatched(
            @RequestBody DispatchPayload payload) {
        if (payload == null || payload.deliveryId() == null || payload.deliveryId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "deliveryId is required"));
        }
        InboundDelivery existing = inboundDeliveryRepository.findById(payload.deliveryId()).orElse(null);
        InboundDelivery row = (existing != null) ? existing : new InboundDelivery();
        row.setDeliveryId(payload.deliveryId());
        row.setContractId(payload.contractId());
        row.setItem(payload.item());
        row.setQuantity(payload.quantity());
        row.setDeliveryDate(payload.date());
        row.setVendorStatus(payload.status());
        inboundDeliveryRepository.save(row);
        log.info("Received vendor dispatch for delivery {} (vendor status {})",
                payload.deliveryId(), payload.status());

        // Note: no central push here — this endpoint is open (no JWT, no toUserId
        // available) and the Vendor module already emitted the DELIVERY_DISPATCHED
        // central notification targeted at the right PM userId.

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "deliveryId", payload.deliveryId()));
    }

    @GetMapping("/internal/deliveries/{deliveryId}/site-status")
    public ResponseEntity<Map<String, Object>> getSiteStatus(
            @PathVariable("deliveryId") String deliveryId) {
        InboundDelivery row = inboundDeliveryRepository.findById(deliveryId).orElse(null);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("deliveryId", deliveryId);
        if (row == null) {
            body.put("known", false);
            return ResponseEntity.ok(body);
        }
        body.put("known", true);
        body.put("siteStatus", row.getSiteStatus());
        body.put("siteRemarks", row.getSiteRemarks());
        body.put("receivedAt", row.getReceivedAt());
        body.put("vendorStatus", row.getVendorStatus());
        return ResponseEntity.ok(body);
    }

    /* ── 2. Site Officer confirmation endpoint ─────────────────────────── */

    @PatchMapping("/api/siteops/deliveries/{deliveryId}/confirm")
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','PROJECT_MANAGER','ADMIN')")
    public ResponseEntity<?> confirmDelivery(
            @PathVariable String deliveryId,
            @RequestParam("status") String status,
            @RequestBody(required = false) Map<String, String> body) {

        if (!"RECEIVED".equalsIgnoreCase(status) && !"NOT_RECEIVED".equalsIgnoreCase(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status must be RECEIVED or NOT_RECEIVED"));
        }
        InboundDelivery row = inboundDeliveryRepository.findById(deliveryId).orElse(null);
        if (row == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Delivery not found: " + deliveryId));
        }
        String remarks = (body != null) ? body.get("remarks") : null;
        String confirmedBy = currentUserId();
        String auth = currentAuthorizationHeader();

        // Persist the site outcome locally first.
        row.setSiteStatus(status.toUpperCase());
        row.setSiteRemarks(remarks);
        row.setReceivedAt(LocalDateTime.now());
        inboundDeliveryRepository.save(row);

        // Push back to Vendor — fire-and-forget. The Vendor module's confirmFromSite
        // already handles its own central notifications (DELIVERY_CONFIRMED to
        // PM + vendor). We only emit the SITE-side echo here back to the engineer
        // who confirmed, for their own bell history.
        try {
            vendorClient.updateDeliverySiteStatus(deliveryId, status.toUpperCase(), remarks);
            log.info("Notified vendor of delivery {} site outcome {}", deliveryId, status);
        } catch (Exception ex) {
            log.warn("Could not notify vendor of delivery {} confirmation: {}",
                    deliveryId, ex.getMessage());
        }

        // --- Push DELIVERY_CONFIRMED echo to the site engineer (self) ---
        if (confirmedBy != null && !confirmedBy.isBlank()) {
            String confirmMsg = String.format(
                    "You confirmed delivery %s as %s%s",
                    deliveryId, row.getSiteStatus(),
                    (remarks != null && !remarks.isBlank()) ? " — " + remarks : ".");
            pushCentral("DELIVERY_CONFIRMED", confirmMsg,
                    confirmedBy,                        // fromUserId — themselves
                    "SITE_ENGINEER", confirmedBy,       // toUserId — themselves
                    deliveryId,
                    auth);
        }

        return ResponseEntity.ok(Map.of(
                "deliveryId", deliveryId,
                "siteStatus", row.getSiteStatus(),
                "vendorNotified", "best-effort"));
    }

    /* ── Convenience read endpoints for the Site Officer's portal ──────── */

    @GetMapping("/api/siteops/deliveries")
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','PROJECT_MANAGER','ADMIN')")
    public ResponseEntity<List<InboundDelivery>> listAll() {
        return ResponseEntity.ok(inboundDeliveryRepository.findAll());
    }

    @GetMapping("/api/siteops/deliveries/pending")
    @PreAuthorize("hasAnyRole('SITE_ENGINEER','PROJECT_MANAGER','ADMIN')")
    public ResponseEntity<List<InboundDelivery>> listPending() {
        return ResponseEntity.ok(inboundDeliveryRepository.findBySiteStatusIsNull());
    }

    /* ── Helpers ──────────────────────────────────────────────────────── */

    /** Pulls the userId from the authenticated principal. */
    private String currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) return null;
            return auth.getPrincipal().toString();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Pulls the raw Authorization header (Bearer token) from credentials. */
    private String currentAuthorizationHeader() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getCredentials() == null) return null;
            String token = auth.getCredentials().toString();
            return token.startsWith("Bearer ") ? token : "Bearer " + token;
        } catch (Exception ex) {
            return null;
        }
    }

    private void pushCentral(String eventType, String message,
                             String fromUserId,
                             String toRole, String toUserId,
                             String referenceId,
                             String authorization) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(
                    new NotificationCreateRequest(
                            eventType,
                            message,
                            "siteops-service",
                            "SITE_ENGINEER",
                            fromUserId,
                            toRole,
                            toUserId,
                            referenceId,
                            null
                    ),
                    authorization);
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }

    /* ── Inbound payload from Vendor ────────────────────────────────────── */

    public record DispatchPayload(
            String deliveryId,
            String contractId,
            String item,
            Integer quantity,
            LocalDate date,
            String status
    ) {}
}