package com.buildsmart.safety.service.impl;

import com.buildsmart.safety.client.NotificationServiceClient;
import com.buildsmart.safety.client.NotificationServiceClient.NotificationPayload;
import com.buildsmart.safety.client.PmNotificationClient;
import com.buildsmart.safety.client.UserClient;
import com.buildsmart.safety.client.dto.PmInternalNotificationRequest;
import com.buildsmart.safety.client.dto.UserDto;
import com.buildsmart.safety.common.exception.ResourceNotFoundException;
import com.buildsmart.safety.common.util.IdGeneratorUtil;
import com.buildsmart.safety.domain.model.AssignedTask;
import com.buildsmart.safety.domain.model.AssignedTaskStatus;
import com.buildsmart.safety.domain.model.Incident;
import com.buildsmart.safety.domain.model.IncidentSeverity;
import com.buildsmart.safety.domain.model.IncidentStatus;
import com.buildsmart.safety.domain.repository.AssignedTaskRepository;
import com.buildsmart.safety.domain.repository.IncidentRepository;
import com.buildsmart.safety.exception.InvalidStatusTransitionException;
import com.buildsmart.safety.exception.UnauthorizedOperationException;
import com.buildsmart.safety.security.JwtUtil;
import com.buildsmart.safety.service.IncidentService;
import com.buildsmart.safety.validator.IncidentValidator;
import com.buildsmart.safety.web.dto.IncidentDtos.CreateIncidentRequest;
import com.buildsmart.safety.web.dto.IncidentDtos.IncidentResponse;
import com.buildsmart.safety.web.mapper.IncidentMapper;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Incident lifecycle service.
 *
 * Notification policy: every event is pushed to the central notification-service
 * with a specific toUserId. The legacy local NotificationService and its
 * SafetyNotification entity have been removed — officers and PMs read their
 * notifications from the central service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentValidator incidentValidator;
    private final AssignedTaskRepository assignedTaskRepository;
    private final UserClient userClient;
    private final JwtUtil jwtUtil;
    private final PmNotificationClient pmNotificationClient;

    /**
     * Central notification service client — every incident event lands in the
     * platform-wide notification-service, targeted at the specific user. Optional
     * so unit tests / startup without the bean still work.
     */
    @Autowired(required = false)
    private NotificationServiceClient notificationServiceClient;

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
            IncidentStatus.OPEN,                Set.of(IncidentStatus.UNDER_INVESTIGATION, IncidentStatus.RESOLVED),
            IncidentStatus.UNDER_INVESTIGATION, Set.of(IncidentStatus.RESOLVED),
            IncidentStatus.RESOLVED,            Set.of(IncidentStatus.CLOSED),
            IncidentStatus.CLOSED,              Set.of()
    );

    @Override
    public IncidentResponse create(CreateIncidentRequest request) {
        incidentValidator.validate(request);

        // Resolve current user from IAM (live — gets fresh name + status)
        UserDto reporter = resolveCurrentUser();

        // Guard: user must be ACTIVE
        if (!"ACTIVE".equals(reporter.status())) {
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + reporter.status());
        }

        // Guard: officer must have an active (PENDING) assigned task for this project.
        // Once all tasks are completed they are no longer part of the project.
        boolean hasActiveTask = assignedTaskRepository
                .findByAssignedToAndStatusOrderBySyncedAtDesc(reporter.userId(), AssignedTaskStatus.PENDING)
                .stream()
                .anyMatch(t -> t.getProjectId().equals(request.projectId()));
        if (!hasActiveTask) {
            throw new UnauthorizedOperationException(
                    "You do not have an active assigned task for project " + request.projectId()
                            + ". Sync your tasks first, or you may no longer be assigned to this project.");
        }

        Incident last = incidentRepository.findTopByOrderByIncidentIdDesc();
        Incident incident = new Incident();
        incident.setIncidentId(IdGeneratorUtil.nextIncidentId(last == null ? null : last.getIncidentId()));
        incident.setProjectId(request.projectId());
        incident.setDescription(request.description());
        incident.setSeverity(request.severity());
        incident.setReportedBy(reporter.userId());
        incident.setReportedByName(reporter.name());
        incident.setDate(LocalDate.now());
        incident.setStatus(IncidentStatus.OPEN);

        Incident saved = incidentRepository.save(incident);
        notifyIncidentReported(saved);
        return IncidentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse get(String id) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
        return IncidentMapper.toResponse(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> search(Optional<String> projectId, Optional<IncidentStatus> status,
                                         Optional<IncidentSeverity> severity,
                                         Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo,
                                         Optional<String> reportedBy,
                                         Pageable pageable) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        Specification<Incident> spec = (root, query, cb) -> cb.conjunction();
        if (projectId.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("projectId"), projectId.get()));
        if (status.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status.get()));
        if (severity.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("severity"), severity.get()));
        if (dateFrom.isPresent())
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("date"), dateFrom.get()));
        if (dateTo.isPresent())
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("date"), dateTo.get()));
        if (reportedBy.isPresent())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("reportedBy"), reportedBy.get()));

        return incidentRepository.findAll(spec, pageable).map(IncidentMapper::toResponse);
    }

    @Override
    public IncidentResponse updateStatus(String id, IncidentStatus newStatus) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));

        IncidentStatus oldStatus = incident.getStatus();
        if (!ALLOWED_TRANSITIONS.get(oldStatus).contains(newStatus))
            throw new InvalidStatusTransitionException(
                    "Cannot transition incident from " + oldStatus + " to " + newStatus);

        incident.setStatus(newStatus);
        Incident saved = incidentRepository.save(incident);
        notifyIncidentStatusChanged(saved, oldStatus);
        return IncidentMapper.toResponse(saved);
    }

    @Override
    public void delete(String id) {
        UserDto caller = resolveCurrentUser();
        if (!"ACTIVE".equals(caller.status()))
            throw new UnauthorizedOperationException(
                    "Your account is not active. Current status: " + caller.status());

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
        if (incident.getStatus() != IncidentStatus.OPEN)
            throw new UnauthorizedOperationException(
                    "Cannot delete an incident with status: " + incident.getStatus()
                            + ". Only OPEN incidents can be deleted.");
        incidentRepository.deleteById(id);
    }

    // ── Notification routing (formerly in NotificationServiceImpl) ───────

    private void notifyIncidentReported(Incident incident) {
        Optional<String> pmIdOpt = resolvePmUserId(incident.getProjectId());

        // Legacy PM-push (writes to PM's local notification table)
        pmIdOpt.ifPresent(pmId ->
                pushToPm(new PmInternalNotificationRequest(
                        IdGeneratorUtil.nextNotificationId(null),
                        incident.getProjectId(),
                        "SAFETY_INCIDENT_REPORTED",
                        "[Safety] New Incident Reported",
                        String.format("Officer %s reported incident %s for project %s with severity %s.",
                                incident.getReportedBy(), incident.getIncidentId(),
                                incident.getProjectId(), incident.getSeverity()),
                        false,
                        java.time.LocalDateTime.now(),
                        incident.getReportedBy(),
                        pmId,
                        null, null, null))
        );

        // --- Push INCIDENT_REPORTED to PM via central notification-service ---
        String msg = String.format("Officer %s reported incident %s for project %s with severity %s.",
                incident.getReportedBy(), incident.getIncidentId(),
                incident.getProjectId(), incident.getSeverity());
        pmIdOpt.ifPresent(pmId ->
                pushCentral("INCIDENT_REPORTED", msg,
                        incident.getReportedBy(),         // fromUserId — the officer
                        "PROJECT_MANAGER", pmId,          // toUserId — the PM
                        incident.getIncidentId()));

        // Self-echo to the reporting officer's bell
        pushCentral("INCIDENT_REPORTED",
                String.format("You reported incident %s for project %s (severity %s).",
                        incident.getIncidentId(), incident.getProjectId(), incident.getSeverity()),
                incident.getReportedBy(),
                "SAFETY_OFFICER", incident.getReportedBy(),
                incident.getIncidentId());
    }

    private void notifyIncidentStatusChanged(Incident incident, IncidentStatus oldStatus) {
        Optional<String> pmIdOpt = resolvePmUserId(incident.getProjectId());

        // Legacy PM-push
        pmIdOpt.ifPresent(pmId ->
                pushToPm(new PmInternalNotificationRequest(
                        IdGeneratorUtil.nextNotificationId(null),
                        incident.getProjectId(),
                        "SAFETY_INCIDENT_UPDATED",
                        "[Safety] Incident Status Updated",
                        String.format("Incident %s (project %s) status changed from %s to %s.",
                                incident.getIncidentId(), incident.getProjectId(), oldStatus, incident.getStatus()),
                        false,
                        java.time.LocalDateTime.now(),
                        incident.getReportedBy(),
                        pmId,
                        null, null, null))
        );

        // --- Push to PM via central ---
        String msg = String.format("Incident %s (project %s) status changed from %s to %s.",
                incident.getIncidentId(), incident.getProjectId(), oldStatus, incident.getStatus());
        pmIdOpt.ifPresent(pmId ->
                pushCentral("INCIDENT_STATUS_CHANGED", msg,
                        incident.getReportedBy(),
                        "PROJECT_MANAGER", pmId,
                        incident.getIncidentId()));

        // Self-echo to officer
        pushCentral("INCIDENT_STATUS_CHANGED",
                String.format("Your incident %s status updated to %s.",
                        incident.getIncidentId(), incident.getStatus()),
                null,
                "SAFETY_OFFICER", incident.getReportedBy(),
                incident.getIncidentId());
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
     * IAM validates it, queries DB, returns live name + status.
     * Falls back to JWT claims if IAM is unreachable.
     */
    private UserDto resolveCurrentUser() {
        String bearerToken = getAuthorizationHeader();
        String token = bearerToken.substring(7);
        try {
            UserClient.IamProfileResponse response = userClient.getCurrentUserProfile(bearerToken);
            if (response == null || response.data() == null) {
                // Circuit breaker fallback returned null — IAM unreachable, use JWT claims
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