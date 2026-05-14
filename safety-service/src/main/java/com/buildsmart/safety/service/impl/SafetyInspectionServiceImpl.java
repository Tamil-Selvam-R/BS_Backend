package com.buildsmart.safety.service.impl;

import com.buildsmart.safety.client.NotificationServiceClient;
import com.buildsmart.safety.client.NotificationServiceClient.NotificationPayload;
import com.buildsmart.safety.client.PmNotificationClient;
import com.buildsmart.safety.client.UserClient;
import com.buildsmart.safety.client.dto.PmInternalNotificationRequest;
import com.buildsmart.safety.client.dto.UserDto;
import com.buildsmart.safety.common.exception.DuplicateResourceException;
import com.buildsmart.safety.common.exception.ResourceNotFoundException;
import com.buildsmart.safety.common.util.IdGeneratorUtil;
import com.buildsmart.safety.domain.model.AssignedTask;
import com.buildsmart.safety.domain.model.AssignedTaskStatus;
import com.buildsmart.safety.domain.model.InspectionStatus;
import com.buildsmart.safety.domain.model.SafetyInspection;
import com.buildsmart.safety.domain.repository.AssignedTaskRepository;
import com.buildsmart.safety.domain.repository.SafetyInspectionRepository;
import com.buildsmart.safety.exception.InvalidStatusTransitionException;
import com.buildsmart.safety.exception.TaskAlreadyCompletedException;
import com.buildsmart.safety.exception.TaskNotAssignedToOfficerException;
import com.buildsmart.safety.exception.UnauthorizedOperationException;
import com.buildsmart.safety.security.JwtUtil;
import com.buildsmart.safety.service.SafetyInspectionService;
import com.buildsmart.safety.validator.SafetyInspectionValidator;
import com.buildsmart.safety.web.dto.InspectionDtos.CreateInspectionRequest;
import com.buildsmart.safety.web.dto.InspectionDtos.InspectionResponse;
import com.buildsmart.safety.web.mapper.InspectionMapper;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Safety inspection lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local NotificationService has been
 * removed — officers and PMs read their notifications from the central service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SafetyInspectionServiceImpl implements SafetyInspectionService {

    private final SafetyInspectionRepository inspectionRepository;
    private final SafetyInspectionValidator inspectionValidator;
    private final AssignedTaskRepository assignedTaskRepository;
    private final UserClient userClient;
    private final JwtUtil jwtUtil;
    private final PmNotificationClient pmNotificationClient;

    /**
     * Central notification service client — inspection events route to the
     * specific user via toUserId. Optional so unit tests / startup without the
     * bean still work.
     */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    private static final Map<InspectionStatus, Set<InspectionStatus>> ALLOWED_TRANSITIONS = Map.of(
            InspectionStatus.SCHEDULED,     Set.of(InspectionStatus.IN_PROGRESS),
            InspectionStatus.IN_PROGRESS,   Set.of(InspectionStatus.COMPLETED, InspectionStatus.NON_COMPLIANT),
            InspectionStatus.COMPLETED,     Set.of(InspectionStatus.CLOSED),
            InspectionStatus.NON_COMPLIANT, Set.of(InspectionStatus.CLOSED),
            InspectionStatus.CLOSED,        Set.of()
    );

    @Override
    public InspectionResponse create(CreateInspectionRequest request) {
        inspectionValidator.validate(request);

        // Resolve current officer from IAM (live — gets fresh name + status)
        UserDto officer = resolveCurrentUser();

        // Guard: user must be ACTIVE
        if (!"ACTIVE".equals(officer.status())) {
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + officer.status());
        }

        // Role guard
        if (!"SAFETY_OFFICER".equals(officer.role()) && !"ADMIN".equals(officer.role())) {
            throw new UnauthorizedOperationException(
                    "Only users with role SAFETY_OFFICER can schedule inspections");
        }

        // ── Project validation via local assigned_tasks table ──────────────
        // We do NOT call PM service (officer JWT lacks ADMIN/PROJECT_MANAGER role).
        // A project is "valid" for this officer if they have at least one AssignedTask
        // for it, OR if they are creating a free (no task) inspection — in which case
        // we just proceed (incident-style open reporting).
        // If assignedTaskId is provided the task-level validation below covers project ownership.
        boolean hasTaskForProject = !assignedTaskRepository
                .findByAssignedToAndProjectId(officer.userId(), request.projectId()).isEmpty();
        if (request.assignedTaskId() != null && !request.assignedTaskId().isBlank() && !hasTaskForProject) {
            throw new TaskNotAssignedToOfficerException(request.assignedTaskId(), officer.userId());
        }

        // ── Assigned-task validation (only when caller provides assignedTaskId) ──
        AssignedTask linkedTask = null;
        if (request.assignedTaskId() != null && !request.assignedTaskId().isBlank()) {
            linkedTask = assignedTaskRepository.findById(request.assignedTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Assigned task not found: " + request.assignedTaskId()));

            // Task must belong to this officer
            if (!linkedTask.getAssignedTo().equals(officer.userId())) {
                throw new TaskNotAssignedToOfficerException(request.assignedTaskId(), officer.userId());
            }

            // Task must be for the same project
            if (!linkedTask.getProjectId().equals(request.projectId())) {
                throw new TaskNotAssignedToOfficerException(request.assignedTaskId(), officer.userId());
            }

            // Task must still be PENDING
            if (linkedTask.getStatus() == AssignedTaskStatus.COMPLETED) {
                throw new TaskAlreadyCompletedException(request.assignedTaskId());
            }
        }

        // Duplicate guard — same inspection type cannot be active on the same project today.
        List<InspectionStatus> activeStatuses = List.of(InspectionStatus.SCHEDULED, InspectionStatus.IN_PROGRESS);
        if (inspectionRepository.existsByProjectIdAndDateAndInspectionTypeAndStatusIn(
                request.projectId(), LocalDate.now(), request.inspectionType(), activeStatuses)) {
            throw new DuplicateResourceException(
                    "An active " + request.inspectionType() + " inspection already exists "
                            + "for project " + request.projectId() + " today.");
        }

        SafetyInspection last = inspectionRepository.findTopByOrderByInspectionIdDesc();
        SafetyInspection inspection = new SafetyInspection();
        inspection.setInspectionId(IdGeneratorUtil.nextInspectionId(last == null ? null : last.getInspectionId()));
        inspection.setProjectId(request.projectId());
        inspection.setOfficerId(officer.userId());
        inspection.setOfficerName(officer.name());
        inspection.setInspectionType(request.inspectionType());
        inspection.setFindings(request.findings());
        inspection.setDate(LocalDate.now());
        inspection.setStatus(InspectionStatus.SCHEDULED);
        if (request.assignedTaskId() != null && !request.assignedTaskId().isBlank()) {
            inspection.setAssignedTaskId(request.assignedTaskId());
        }

        SafetyInspection saved = inspectionRepository.save(inspection);
        notifyInspectionScheduled(saved);
        return InspectionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionResponse get(String id) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        SafetyInspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found: " + id));
        return InspectionMapper.toResponse(inspection);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionResponse> search(Optional<String> projectId, Optional<InspectionStatus> status,
                                           Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo,
                                           Optional<String> officerId,
                                           Pageable pageable) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        Specification<SafetyInspection> spec = (root, query, cb) -> cb.conjunction();
        if (projectId.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("projectId"), projectId.get()));
        if (status.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status.get()));
        if (dateFrom.isPresent())
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("date"), dateFrom.get()));
        if (dateTo.isPresent())
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("date"), dateTo.get()));
        if (officerId.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("officerId"), officerId.get()));

        return inspectionRepository.findAll(spec, pageable).map(InspectionMapper::toResponse);
    }

    @Override
    public InspectionResponse updateStatus(String id, InspectionStatus newStatus) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        SafetyInspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found: " + id));

        InspectionStatus oldStatus = inspection.getStatus();
        if (!ALLOWED_TRANSITIONS.get(oldStatus).contains(newStatus))
            throw new InvalidStatusTransitionException(
                    "Cannot transition inspection from " + oldStatus + " to " + newStatus);

        inspection.setStatus(newStatus);
        SafetyInspection saved = inspectionRepository.save(inspection);
        notifyInspectionStatusChanged(saved, oldStatus);

        // When the inspection is marked COMPLETED, automatically complete the linked task
        if (newStatus == InspectionStatus.COMPLETED && saved.getAssignedTaskId() != null) {
            assignedTaskRepository.findById(saved.getAssignedTaskId()).ifPresent(task -> {
                task.setStatus(AssignedTaskStatus.COMPLETED);
                task.setLinkedInspectionId(saved.getInspectionId());
                task.setCompletedAt(LocalDateTime.now());
                assignedTaskRepository.save(task);
                log.info("Task {} marked COMPLETED via inspection {}", task.getId(), saved.getInspectionId());
            });
        }

        return InspectionMapper.toResponse(saved);
    }

    @Override
    public void delete(String id) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        SafetyInspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found: " + id));
        if (inspection.getStatus() != InspectionStatus.SCHEDULED)
            throw new UnauthorizedOperationException(
                    "Cannot delete an inspection with status: " + inspection.getStatus()
                            + ". Only SCHEDULED inspections can be deleted.");
        inspectionRepository.deleteById(id);
    }

    // ── Notification routing (formerly in NotificationServiceImpl) ───────

    private void notifyInspectionScheduled(SafetyInspection inspection) {
        Optional<String> pmIdOpt = resolvePmUserId(inspection.getProjectId());

        // Legacy PM-push
        pmIdOpt.ifPresent(pmId ->
                pushToPm(new PmInternalNotificationRequest(
                        IdGeneratorUtil.nextNotificationId(null),
                        inspection.getProjectId(),
                        "SAFETY_INSPECTION_SCHEDULED",
                        "[Safety] New Inspection Scheduled",
                        String.format("Officer %s scheduled a %s inspection (%s) for project %s.",
                                inspection.getOfficerId(), inspection.getInspectionType(),
                                inspection.getInspectionId(), inspection.getProjectId()),
                        false,
                        java.time.LocalDateTime.now(),
                        inspection.getOfficerId(),
                        pmId,
                        null, null, null))
        );

        // --- Push to PM via central ---
        String msg = String.format("Officer %s scheduled %s inspection (%s) for project %s.",
                inspection.getOfficerId(), inspection.getInspectionType(),
                inspection.getInspectionId(), inspection.getProjectId());
        pmIdOpt.ifPresent(pmId ->
                pushCentral("INSPECTION_SCHEDULED", msg,
                        inspection.getOfficerId(),
                        "PROJECT_MANAGER", pmId,
                        inspection.getInspectionId()));

        // Self-echo to officer
        pushCentral("INSPECTION_SCHEDULED",
                String.format("You scheduled a %s inspection (%s) for project %s.",
                        inspection.getInspectionType(), inspection.getInspectionId(), inspection.getProjectId()),
                inspection.getOfficerId(),
                "SAFETY_OFFICER", inspection.getOfficerId(),
                inspection.getInspectionId());
    }

    private void notifyInspectionStatusChanged(SafetyInspection inspection, InspectionStatus oldStatus) {
        Optional<String> pmIdOpt = resolvePmUserId(inspection.getProjectId());

        // Legacy PM-push
        pmIdOpt.ifPresent(pmId ->
                pushToPm(new PmInternalNotificationRequest(
                        IdGeneratorUtil.nextNotificationId(null),
                        inspection.getProjectId(),
                        "SAFETY_INSPECTION_UPDATED",
                        "[Safety] Inspection Status Updated",
                        String.format("Officer %s updated inspection %s (project %s) from %s to %s.",
                                inspection.getOfficerId(), inspection.getInspectionId(),
                                inspection.getProjectId(), oldStatus, inspection.getStatus()),
                        false,
                        java.time.LocalDateTime.now(),
                        inspection.getOfficerId(),
                        pmId,
                        null, null, null))
        );

        // --- Push to PM via central ---
        String msg = String.format("Officer %s updated inspection %s (project %s) from %s to %s.",
                inspection.getOfficerId(), inspection.getInspectionId(),
                inspection.getProjectId(), oldStatus, inspection.getStatus());
        pmIdOpt.ifPresent(pmId ->
                pushCentral("INSPECTION_STATUS_CHANGED", msg,
                        inspection.getOfficerId(),
                        "PROJECT_MANAGER", pmId,
                        inspection.getInspectionId()));

        // Self-echo
        pushCentral("INSPECTION_STATUS_CHANGED",
                String.format("Your inspection %s status updated to %s.",
                        inspection.getInspectionId(), inspection.getStatus()),
                null,
                "SAFETY_OFFICER", inspection.getOfficerId(),
                inspection.getInspectionId());
    }

    /**
     * Resolves the PM userId via the project's most-recent AssignedTask
     * (whose assignedBy field is the PM who assigned it).
     */
    private Optional<String> resolvePmUserId(String projectId) {
        return assignedTaskRepository.findByProjectIdOrderBySyncedAtDesc(projectId)
                .stream()
                .map(AssignedTask::getAssignedBy)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    private void pushToPm(PmInternalNotificationRequest request) {
        try {
            String bearerToken = getAuthorizationHeader();
            pmNotificationClient.createInternal(request, bearerToken);
        } catch (Exception e) {
            log.warn("Could not push safety notification to PM service: {}", e.getMessage());
        }
    }

    /**
     * Fire-and-forget push to the central notification-service.
     * toUserId is required; skipped silently if missing.
     */
    private void pushCentral(String eventType, String message,
                             String fromUserId,
                             String toRole, String toUserId,
                             String referenceId) {
        if (notificationServiceClient == null) return;
        if (toUserId == null || toUserId.isBlank()) {
            log.warn("Skipping central notification (event={}, ref={}): toUserId missing",
                    eventType, referenceId);
            return;
        }
        try {
            notificationServiceClient.create(new NotificationPayload(
                    eventType,
                    message,
                    "safety-service",
                    "SAFETY_OFFICER",
                    fromUserId,
                    toRole,
                    toUserId,
                    referenceId,
                    null));
        } catch (Exception ex) {
            log.warn("notification-service push failed (event={}, toUserId={}, ref={}): {}",
                    eventType, toUserId, referenceId, ex.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Calls IAM GET /users/profile forwarding the user's own JWT.
     */
    private UserDto resolveCurrentUser() {
        String bearerToken = getAuthorizationHeader();
        String token = bearerToken.substring(7);
        try {
            UserClient.IamProfileResponse response = userClient.getCurrentUserProfile(bearerToken);
            if (response == null || response.data() == null) {
                log.warn("IAM unavailable (circuit breaker open) — falling back to JWT claims");
                return jwtFallback(token);
            }
            UserClient.UserData d = response.data();
            return new UserDto(d.userId(), d.name(), d.email(), d.role(), d.status());
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new UnauthorizedOperationException("Invalid or expired token");
        } catch (FeignException.NotFound e) {
            log.warn("IAM profile not found for current user — falling back to JWT claims");
            return jwtFallback(token);
        } catch (FeignException e) {
            log.warn("IAM unreachable — falling back to JWT claims");
            return jwtFallback(token);
        }
    }

    private UserDto jwtFallback(String token) {
        return new UserDto(
                jwtUtil.extractUserId(token),
                jwtUtil.extractName(token),
                jwtUtil.extractEmail(token),
                jwtUtil.extractRoles(token).stream().findFirst().orElse(""),
                "ACTIVE"
        );
    }

    private String getAuthorizationHeader() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("No active HTTP request");
        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header;
        throw new IllegalStateException("Authorization header missing");
    }
}