package com.buildsmart.vendor.service.impl;

import com.buildsmart.vendor.client.NotificationServiceClient;
import com.buildsmart.vendor.client.ProjectManagerClient;
import com.buildsmart.vendor.client.dto.NotificationCreateRequest;
import com.buildsmart.vendor.client.dto.ProjectDTO;
import com.buildsmart.vendor.dto.response.ContractResponse;
import com.buildsmart.vendor.dto.request.ContractRequest;
import com.buildsmart.vendor.enums.ContractStatus;
import com.buildsmart.vendor.exception.CustomExceptions.ContractNotFoundException;
import com.buildsmart.vendor.exception.CustomExceptions.ProjectNotFoundException;
import com.buildsmart.vendor.entity.Contract;
import com.buildsmart.vendor.repository.ContractRepository;
import com.buildsmart.vendor.service.ContractService;
import com.buildsmart.vendor.util.IdGeneratorUtil;
import com.buildsmart.vendor.validator.ContractValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Contract lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local VendorNotificationService has been
 * removed — vendors read their notifications from the central service's
 * /api/notifications endpoint, not from a vendor-local table.
 */
@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractServiceImpl.class);

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ContractValidator contractValidator;

    @Autowired
    private ProjectManagerClient projectManagerClient;

    @Autowired
    private com.buildsmart.vendor.service.VendorOwnershipService vendorOwnershipService;

    /**
     * Central notification service client — pushes every contract event to the
     * platform-wide notification-service so the bell icon picks it up.
     * Every push targets a specific user via toUserId.
     * Fire-and-forget: failures must never roll back the contract transaction.
     */
    @Autowired
    private NotificationServiceClient notificationServiceClient;

    @Override
    public Page<ContractResponse> getAllContracts(Pageable pageable) {
        return contractRepository.findAll(pageable).map(this::toDTO);
    }

    @Override
    public ContractResponse getContractById(String id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ContractNotFoundException(id));
        return toDTO(contract);
    }

    @Override
    public List<ContractResponse> getContractsByVendorId(String vendorId) {
        List<Contract> contracts = contractRepository.findByVendorId(vendorId);
        List<ContractResponse> dtoList = new ArrayList<>();
        for (Contract contract : contracts) {
            dtoList.add(toDTO(contract));
        }
        return dtoList;
    }

    @Override
    public List<ContractResponse> getContractsByStatus(ContractStatus status) {
        List<Contract> contracts = contractRepository.findByStatus(status);
        List<ContractResponse> dtoList = new ArrayList<>();
        for (Contract contract : contracts) {
            dtoList.add(toDTO(contract));
        }
        return dtoList;
    }

    @Override
    public ContractResponse createContract(ContractRequest request, String vendorId) {
        // Hard precondition (contract-create must be linked to a real PM project):
        // the project referenced by this contract MUST exist in the Project Manager
        // module. We fetch it eagerly so the vendor sees a clear "Project not found"
        // error instead of a silently-skipped PM notification.
        //
        // Also: we capture the PM userId here so we can route the central
        // notification directly to the project's manager.
        String pmUserIdForNotification = null;
        if (request.getProjectId() != null && !request.getProjectId().isBlank()) {
            try {
                ProjectDTO project = projectManagerClient.getProjectById(request.getProjectId());
                if (project == null) {
                    log.warn("PM unreachable while verifying projectId={} for contract creation; proceeding without strict check",
                            request.getProjectId());
                } else {
                    pmUserIdForNotification = project.projectManager();
                }
            } catch (feign.FeignException.NotFound nf) {
                throw new ProjectNotFoundException(request.getProjectId());
            } catch (feign.FeignException fe) {
                // Non-404 transport errors: degrade rather than block.
                log.warn("PM transport error while verifying projectId={}: {}", request.getProjectId(), fe.getMessage());
            }
        }

        // Vendor ownership check. vendorId is the authenticated vendor resolved
        // from the JWT in ContractController. If a taskId is supplied, verify
        // both project and task ownership in a single PM call; otherwise just
        // project.
        if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
            vendorOwnershipService.requireTaskOwnedByVendor(
                    request.getProjectId(), request.getTaskId(), vendorId);
        } else {
            vendorOwnershipService.requireProjectOwnedByVendor(
                    request.getProjectId(), vendorId);
        }

        log.info("createContract: validation start — vendorId={}, projectId={}, taskId={}, startDate={}, endDate={}, value={}, status={}",
                vendorId, request.getProjectId(), request.getTaskId(),
                request.getStartDate(), request.getEndDate(), request.getValue(), request.getStatus());
        contractValidator.validate(request, vendorId);
        log.info("createContract: validation passed — proceeding to duplicate check");

        // Reject exact-duplicate contracts (same vendor + project + task + dates + value)
        if (contractRepository.existsDuplicate(
                vendorId,
                request.getProjectId(),
                request.getTaskId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getValue())) {
            throw new IllegalArgumentException(
                    "A contract with the same vendor, project, task, dates and value already exists.");
        }

        String lastId = contractRepository.findTopByOrderByContractIdDesc()
                .map(Contract::getContractId)
                .orElse(null);
        Contract contract = new Contract();
        contract.setContractId(IdGeneratorUtil.nextContractId(lastId));
        contract.setVendorId(vendorId);
        contract.setProjectId(request.getProjectId());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setValue(request.getValue());
        contract.setStatus(request.getStatus());
        contract.setTaskId(request.getTaskId());
        contract.setDescription(request.getDescription());
        Contract saved = contractRepository.save(contract);

        // --- Notify PM via the cross-service contract endpoint (writes to PM's local table) ---
        try {
            projectManagerClient.notifyContractCreated(
                    saved.getContractId(),
                    saved.getVendorId(),
                    saved.getProjectId(),
                    saved.getTaskId()
            );
            log.info("PM notified of new contract: contractId={}, taskId={}", saved.getContractId(), saved.getTaskId());
        } catch (Exception e) {
            log.warn("Failed to notify PM of contract creation (non-critical): contractId={}, reason={}",
                    saved.getContractId(), e.getMessage());
        }

        // Vendor-facing message (also used by the central push echoed back to the vendor).
        String vendorMsg = String.format(
                "A new contract (%s) has been created for Task ID: %s under Project: %s. " +
                        "Contract period: %s to %s. Please review your contract details.",
                saved.getContractId(),
                saved.getTaskId() != null ? saved.getTaskId() : "N/A",
                saved.getProjectId(),
                saved.getStartDate(),
                saved.getEndDate()
        );

        // --- Push CONTRACT_UPLOADED to central notification-service ---
        // Two notifications go out: one for the PM (project owner) and one for
        // the vendor. Both are fire-and-forget. We send fromUserId=vendorId so
        // the recipient sees "from {vendor}" in the bell dropdown.
        if (pmUserIdForNotification != null && !pmUserIdForNotification.isBlank()) {
            pushCentral(
                    "CONTRACT_UPLOADED",
                    String.format("New contract %s created for project %s with vendor %s (task %s).",
                            saved.getContractId(),
                            saved.getProjectId(),
                            saved.getVendorId(),
                            saved.getTaskId() != null ? saved.getTaskId() : "N/A"),
                    saved.getVendorId(),                  // fromUserId — the vendor created it
                    "PROJECT_MANAGER",                    // toRole
                    pmUserIdForNotification,              // toUserId — the project's PM
                    saved.getContractId());
        } else {
            log.warn("Skipping PM central notification for contract {} — no project manager userId resolved",
                    saved.getContractId());
        }
        pushCentral(
                "CONTRACT_UPLOADED",
                vendorMsg,
                saved.getVendorId(),                      // fromUserId — self (echo)
                "VENDOR",                                 // toRole
                saved.getVendorId(),                      // toUserId — the vendor themselves
                saved.getContractId());

        return toDTO(saved);
    }

    @Override
    public ContractResponse updateContract(String id, ContractRequest request, String vendorId) {
        // Ownership check on the post-update target. Same logic as createContract —
        // the vendor must own whatever project/task they are pointing the contract
        // at after the update.
        if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
            vendorOwnershipService.requireTaskOwnedByVendor(
                    request.getProjectId(), request.getTaskId(), vendorId);
        } else {
            vendorOwnershipService.requireProjectOwnedByVendor(
                    request.getProjectId(), vendorId);
        }

        contractValidator.validate(request, vendorId);
        Contract existing = contractRepository.findById(id)
                .orElseThrow(() -> new ContractNotFoundException(id));

        existing.setVendorId(vendorId);
        existing.setProjectId(request.getProjectId());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setValue(request.getValue());
        existing.setStatus(request.getStatus());
        existing.setTaskId(request.getTaskId());
        existing.setDescription(request.getDescription());
        Contract updated = contractRepository.save(existing);
        return toDTO(updated);
    }

    @Override
    public void deleteContract(String id) {
        contractRepository.deleteById(id);
    }

    @Override
    public org.springframework.data.domain.Page<ContractResponse> getContractsByVendorIdPaginated(
            String vendorId, org.springframework.data.domain.Pageable pageable) {
        return contractRepository.findByVendorId(vendorId, pageable).map(this::toDTO);
    }

    @Override
    public java.util.List<ContractResponse> getContractsByVendorIdAndStatus(
            String vendorId, com.buildsmart.vendor.enums.ContractStatus status) {
        return contractRepository.findByVendorIdAndStatus(vendorId, status)
                .stream().map(this::toDTO).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.List<String> getContractIdsByVendorId(String vendorId) {
        return contractRepository.findByVendorId(vendorId)
                .stream()
                .map(com.buildsmart.vendor.entity.Contract::getContractId)
                .collect(java.util.stream.Collectors.toList());
    }

    private ContractResponse toDTO(Contract contract) {
        ContractResponse dto = new ContractResponse();
        dto.setContractId(contract.getContractId());
        dto.setVendorId(contract.getVendorId());
        dto.setProjectId(contract.getProjectId());
        dto.setStartDate(contract.getStartDate());
        dto.setEndDate(contract.getEndDate());
        dto.setValue(contract.getValue());
        dto.setStatus(contract.getStatus());
        dto.setTaskId(contract.getTaskId());
        dto.setDescription(contract.getDescription());
        return dto;
    }

    /**
     * Helper — fire-and-forget push to the central notification-service.
     * Failures must not roll back the contract transaction.
     *
     * @param eventType   the event name (e.g. "CONTRACT_UPLOADED")
     * @param message     human-readable text for the bell dropdown
     * @param fromUserId  who triggered the event (nullable for system events)
     * @param toRole      recipient role (descriptive tag, REQUIRED)
     * @param toUserId    recipient userId (PRIMARY ROUTING KEY, REQUIRED)
     * @param referenceId business id this notification refers to
     */
    private void pushCentral(String eventType, String message,
                             String fromUserId,
                             String toRole, String toUserId,
                             String referenceId) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId is required but missing",
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
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }
}