package com.buildsmart.vendor.service.impl;

import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.client.dto.ProjectDTO;
import com.buildsmart.vendor.dto.response.DeliveryResponse;
import com.buildsmart.vendor.dto.request.DeliveryRequest;
import com.buildsmart.vendor.enums.DeliveryStatus;
import com.buildsmart.vendor.exception.CustomExceptions.DeliveryNotFoundException;
import com.buildsmart.vendor.entity.Delivery;
import com.buildsmart.vendor.repository.DeliveryRepository;
import com.buildsmart.vendor.service.DeliveryService;
import com.buildsmart.vendor.util.IdGeneratorUtil;
import com.buildsmart.vendor.validator.DeliveryValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Delivery lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local VendorNotificationService has been
 * removed — vendors read their notifications from the central service.
 *
 * Auto-sync from SiteOps: every read calls SiteOps to fetch the latest site
 * confirmation. If the local row is stale, it is updated and a DELIVERY_CONFIRMED
 * push is emitted to the vendor.
 */
@Service
public class DeliveryServiceImpl implements DeliveryService {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryValidator deliveryValidator;

    @Autowired
    private com.buildsmart.vendor.repository.ContractRepository contractRepository;

    @Autowired
    private com.buildsmart.vendor.validator.ProjectDateValidator projectDateValidator;

    /**
     * FEATURE SET 2 — Vendor pushes dispatched deliveries to SiteOps so the
     * Site Officer can later confirm physical receipt. Marked optional so the
     * service still starts if SiteOps is not registered (e.g. unit tests).
     */
    @Autowired(required = false)
    private com.buildsmart.vendor.client.SiteOpsClient siteOpsClient;

    /**
     * PM Feign client — used to resolve the project's manager userId so we
     * can target central notifications directly at the right PM.
     */
    @Autowired(required = false)
    private com.buildsmart.vendor.client.ProjectManagerClient projectManagerClient;

    /**
     * Central notification service client — every delivery lifecycle event
     * (created, dispatched, confirmed) goes here, targeted at the specific
     * user that needs to act. Fire-and-forget.
     */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    private static final org.slf4j.Logger deliveryLog =
            org.slf4j.LoggerFactory.getLogger(DeliveryServiceImpl.class);

    @Override
    public Page<DeliveryResponse> getAllDeliveries(Pageable pageable) {
        return deliveryRepository.findAll(pageable)
                .map(this::syncFromSiteOps)
                .map(this::toDTO);
    }

    @Override
    public DeliveryResponse getDeliveryById(String id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new DeliveryNotFoundException(id));
        delivery = syncFromSiteOps(delivery);
        return toDTO(delivery);
    }

    @Override
    public List<DeliveryResponse> getDeliveriesByContractId(String contractId) {
        List<Delivery> deliveries = deliveryRepository.findByContractId(contractId);
        List<DeliveryResponse> dtoList = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            dtoList.add(toDTO(syncFromSiteOps(delivery)));
        }
        return dtoList;
    }

    @Override
    public List<DeliveryResponse> getDeliveriesByStatus(DeliveryStatus status) {
        List<Delivery> deliveries = deliveryRepository.findByStatus(status);
        List<DeliveryResponse> dtoList = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            dtoList.add(toDTO(syncFromSiteOps(delivery)));
        }
        return dtoList;
    }

    private Delivery syncFromSiteOps(Delivery delivery) {
        if (siteOpsClient == null || delivery == null || delivery.getDeliveryId() == null) {
            return delivery;
        }
        try {
            com.buildsmart.vendor.client.SiteOpsClient.SiteStatusResponse remote =
                    siteOpsClient.getSiteStatus(delivery.getDeliveryId());
            if (remote == null || !remote.known()
                    || remote.siteStatus() == null || remote.siteStatus().isBlank()) {
                return delivery;
            }
            DeliveryStatus remoteStatus;
            try {
                remoteStatus = DeliveryStatus.valueOf(remote.siteStatus().trim().toUpperCase());
            } catch (IllegalArgumentException badEnum) {
                deliveryLog.warn("Ignoring unknown siteStatus '{}' from siteops for delivery {}.",
                        remote.siteStatus(), delivery.getDeliveryId());
                return delivery;
            }
            if (remoteStatus != DeliveryStatus.RECEIVED
                    && remoteStatus != DeliveryStatus.NOT_RECEIVED) {
                return delivery;
            }
            if (remoteStatus == delivery.getStatus()) {
                return delivery;
            }
            DeliveryStatus previousStatus = delivery.getStatus();
            delivery.setStatus(remoteStatus);
            Delivery saved = deliveryRepository.save(delivery);
            deliveryLog.info("Auto-sync: delivery {} status {} → {} from siteops",
                    saved.getDeliveryId(), previousStatus, remoteStatus);
            notifyVendorOfSiteConfirmation(saved, remoteStatus, remote.siteRemarks());
            return saved;
        } catch (Exception ex) {
            deliveryLog.warn("syncFromSiteOps failed for delivery {} (siteops unreachable or error): {}",
                    delivery.getDeliveryId(), ex.getMessage());
            return delivery;
        }
    }

    @Override
    public DeliveryResponse createDelivery(DeliveryRequest request) {
        deliveryValidator.validate(request);

        // Resolve the parent contract + project so we have:
        //  - vendorId (for fromUserId — the sender)
        //  - PM userId (for toUserId on the PM notification)
        com.buildsmart.vendor.entity.Contract parentContract =
                contractRepository.findById(request.getContractId()).orElse(null);
        String pmUserId = null;
        String vendorIdForFrom = (parentContract != null) ? parentContract.getVendorId() : null;
        if (parentContract != null && parentContract.getProjectId() != null) {
            projectDateValidator.validateDateWithinProject(
                    parentContract.getProjectId(), request.getDate(), "Delivery date");
            pmUserId = resolvePmUserId(parentContract.getProjectId());
        }

        String lastId = deliveryRepository.findTopByOrderByDeliveryIdDesc()
                .map(Delivery::getDeliveryId)
                .orElse(null);
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(IdGeneratorUtil.nextDeliveryId(lastId));
        delivery.setContractId(request.getContractId());
        delivery.setDate(request.getDate());
        delivery.setItem(request.getItem());
        delivery.setQuantity(request.getQuantity());
        delivery.setStatus(request.getStatus());
        Delivery saved = deliveryRepository.save(delivery);

        // FEATURE SET 2 step 1 — push delivery to SiteOps. Fire-and-forget.
        notifySiteOpsOfDispatch(saved, pmUserId);

        // --- Push DELIVERY_CREATED to central notification-service ---
        // Two recipients: the PM of the project (so they know a delivery is
        // scheduled) and the vendor themselves (confirmation echo).
        String createdMsg = String.format(
                "Delivery %s scheduled for contract %s — item: %s, qty: %s, date: %s.",
                saved.getDeliveryId(), saved.getContractId(),
                saved.getItem(), saved.getQuantity(), saved.getDate());
        if (pmUserId != null) {
            pushCentral("DELIVERY_CREATED", createdMsg,
                    vendorIdForFrom, "PROJECT_MANAGER", pmUserId, saved.getDeliveryId());
        }
        if (vendorIdForFrom != null) {
            pushCentral("DELIVERY_CREATED", createdMsg,
                    vendorIdForFrom, "VENDOR", vendorIdForFrom, saved.getDeliveryId());
        }

        return toDTO(saved);
    }

    /**
     * Helper — push the new delivery to SiteOps so the Site Officer can confirm
     * receipt, and emit DELIVERY_DISPATCHED to the central service.
     */
    private void notifySiteOpsOfDispatch(Delivery delivery, String pmUserId) {
        if (siteOpsClient == null) return;
        try {
            // Resolve projectId from the contract so SiteOps can scope the delivery
            // to the correct project — site engineers only see deliveries for their projects.
            String projectId = contractRepository.findById(delivery.getContractId())
                    .map(c -> c.getProjectId())
                    .orElse(null);

            siteOpsClient.notifyDeliveryDispatched(
                    new com.buildsmart.vendor.client.SiteOpsClient.DeliveryDispatchPayload(
                            delivery.getDeliveryId(),
                            projectId,               // ← resolved from Contract.projectId
                            delivery.getContractId(),
                            delivery.getItem(),
                            delivery.getQuantity(),
                            delivery.getDate(),
                            delivery.getStatus() != null ? delivery.getStatus().name() : null
                    ));
            deliveryLog.info("Dispatched delivery {} to SiteOps", delivery.getDeliveryId());

            // --- Also push DELIVERY_DISPATCHED to central notification-service ---
            // PM is the audience — they coordinate the site team.
            if (pmUserId != null) {
                String vendorId = contractRepository.findById(delivery.getContractId())
                        .map(c -> c.getVendorId()).orElse(null);
                pushCentral(
                        "DELIVERY_DISPATCHED",
                        String.format("Delivery %s dispatched and is on its way to site.",
                                delivery.getDeliveryId()),
                        vendorId,
                        "PROJECT_MANAGER",
                        pmUserId,
                        delivery.getDeliveryId());
            }
        } catch (Exception ex) {
            deliveryLog.warn("Could not push delivery {} to SiteOps: {}",
                    delivery.getDeliveryId(), ex.getMessage());
        }
    }

    @Override
    public DeliveryResponse updateDelivery(String id, DeliveryRequest request) {
        deliveryValidator.validate(request);

        com.buildsmart.vendor.entity.Contract parentContract =
                contractRepository.findById(request.getContractId()).orElse(null);
        if (parentContract != null && parentContract.getProjectId() != null) {
            projectDateValidator.validateDateWithinProject(
                    parentContract.getProjectId(), request.getDate(), "Delivery date");
        }

        Delivery existing = deliveryRepository.findById(id)
                .orElseThrow(() -> new DeliveryNotFoundException(id));
        existing.setContractId(request.getContractId());
        existing.setDate(request.getDate());
        existing.setItem(request.getItem());
        existing.setQuantity(request.getQuantity());
        existing.setStatus(request.getStatus());
        Delivery updated = deliveryRepository.save(existing);
        return toDTO(updated);
    }

    @Override
    public void deleteDelivery(String id) {
        deliveryRepository.deleteById(id);
    }

    @Override
    public DeliveryResponse pullSiteStatus(String deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        Delivery synced = syncFromSiteOps(delivery);
        return toDTO(synced);
    }

    /**
     * FEATURE SET 2 step 2 — Site → Vendor callback handler.
     */
    @Override
    public DeliveryResponse confirmFromSite(String id, DeliveryStatus status, String remarks) {
        if (status != DeliveryStatus.RECEIVED && status != DeliveryStatus.NOT_RECEIVED) {
            throw new IllegalArgumentException(
                    "Site can only confirm RECEIVED or NOT_RECEIVED. Got: " + status);
        }
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new DeliveryNotFoundException(id));

        delivery.setStatus(status);
        Delivery saved = deliveryRepository.save(delivery);
        deliveryLog.info("Site confirmed delivery {} as {}{}",
                id, status,
                (remarks != null && !remarks.isBlank()) ? " (remarks: " + remarks + ")" : "");

        notifyVendorOfSiteConfirmation(saved, status, remarks);

        return toDTO(saved);
    }

    /**
     * Pushes DELIVERY_CONFIRMED to the central notification-service for both
     * the vendor (primary audience) and the project's PM. Replaces the
     * legacy VendorNotificationService call — the central service is now the
     * single source of truth for the vendor's bell icon.
     */
    private void notifyVendorOfSiteConfirmation(Delivery delivery,
                                                DeliveryStatus status,
                                                String remarks) {
        try {
            String vendorId = resolveVendorIdViaContract(delivery.getContractId());
            String pmUserId = contractRepository.findById(delivery.getContractId())
                    .map(c -> c.getProjectId())
                    .map(this::resolvePmUserId).orElse(null);

            boolean accepted = status == DeliveryStatus.RECEIVED;
            String message = accepted
                    ? String.format(
                    "Site Officer has confirmed delivery %s as RECEIVED.%s",
                    delivery.getDeliveryId(),
                    (remarks != null && !remarks.isBlank())
                            ? " Remarks: " + remarks : "")
                    : String.format(
                    "Site Officer reported delivery %s as NOT RECEIVED. Reason: %s. " +
                            "Please follow up to redispatch.",
                    delivery.getDeliveryId(),
                    (remarks != null && !remarks.isBlank()) ? remarks : "(no reason provided)");

            if (vendorId != null && !vendorId.isBlank()) {
                pushCentral("DELIVERY_CONFIRMED", message,
                        null, "VENDOR", vendorId, delivery.getDeliveryId());
                deliveryLog.info("Vendor {} notified of delivery {} site outcome {}",
                        vendorId, delivery.getDeliveryId(), status);
            } else {
                deliveryLog.warn("Could not resolve vendorId for delivery {} (contractId={}) — vendor notification skipped.",
                        delivery.getDeliveryId(), delivery.getContractId());
            }

            if (pmUserId != null && !pmUserId.isBlank()) {
                pushCentral("DELIVERY_CONFIRMED", message,
                        null, "PROJECT_MANAGER", pmUserId, delivery.getDeliveryId());
            }
        } catch (Exception ex) {
            deliveryLog.warn("Failed to push central notification for delivery {} site confirmation: {}",
                    delivery.getDeliveryId(), ex.getMessage());
        }
    }

    /** Looks up the vendorId from the Contract row referenced by the delivery. */
    private String resolveVendorIdViaContract(String contractId) {
        if (contractId == null || contractId.isBlank()) return null;
        return contractRepository.findById(contractId)
                .map(c -> c.getVendorId())
                .orElse(null);
    }

    /**
     * Looks up the PM userId for a given projectId via the Project Manager
     * service. Returns null if PM is unreachable — the caller falls back to
     * skipping the central notification rather than spamming all PMs.
     */
    private String resolvePmUserId(String projectId) {
        if (projectManagerClient == null || projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            ProjectDTO project = projectManagerClient.getProjectById(projectId);
            return project != null ? project.projectManager() : null;
        } catch (Exception ex) {
            deliveryLog.warn("Could not resolve PM userId for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    @Override
    public org.springframework.data.domain.Page<DeliveryResponse> getDeliveriesByContractIds(
            java.util.List<String> contractIds,
            org.springframework.data.domain.Pageable pageable) {
        if (contractIds == null || contractIds.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }
        return deliveryRepository.findByContractIdIn(contractIds, pageable).map(this::toDTO);
    }

    @Override
    public java.util.List<DeliveryResponse> getDeliveriesByContractIdsAndStatus(
            java.util.List<String> contractIds,
            com.buildsmart.vendor.enums.DeliveryStatus status) {
        if (contractIds == null || contractIds.isEmpty()) {
            return java.util.List.of();
        }
        return deliveryRepository.findByContractIdInAndStatus(contractIds, status)
                .stream().map(this::toDTO).collect(java.util.stream.Collectors.toList());
    }

    private DeliveryResponse toDTO(Delivery delivery) {
        DeliveryResponse dto = new DeliveryResponse();
        dto.setDeliveryId(delivery.getDeliveryId());
        dto.setContractId(delivery.getContractId());
        dto.setDate(delivery.getDate());
        dto.setItem(delivery.getItem());
        dto.setQuantity(delivery.getQuantity());
        dto.setStatus(delivery.getStatus());
        return dto;
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * toUserId is required; if null/blank, the call is skipped to avoid
     * polluting the central service with un-routable rows.
     */
    private void pushCentral(String eventType, String message,
                             String fromUserId,
                             String toRole, String toUserId,
                             String referenceId) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            deliveryLog.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(new NotificationCreateRequest(
                    eventType,
                    message,
                    "vendor-service",
                    "VENDOR",
                    fromUserId,
                    toRole,
                    toUserId,
                    referenceId,
                    null
            ));
        } catch (Exception ex) {
            deliveryLog.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
}