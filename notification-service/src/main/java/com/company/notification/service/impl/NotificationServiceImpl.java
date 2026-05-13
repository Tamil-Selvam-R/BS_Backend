package com.company.notification.service.impl;

import com.company.notification.config.security.AuthenticatedUser;
import com.company.notification.dto.NotificationRequest;
import com.company.notification.dto.NotificationResponse;
import com.company.notification.dto.UnreadCountResponse;
import com.company.notification.exception.AccessDeniedException;
import com.company.notification.exception.ResourceNotFoundException;
import com.company.notification.exception.ServiceDegradedException;
import com.company.notification.model.Notification;
import com.company.notification.repository.NotificationRepository;
import com.company.notification.service.NotificationService;
import com.company.notification.util.SecurityUtil;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * <h2>Resilience strategy</h2>
 *
 * <pre>
 * Operation              CircuitBreaker        Retry                 Bulkhead          Fallback behavior
 * ───────────────────────────────────────────────────────────────────────────────────────────────────────
 * create()               notificationCreate    notificationCreate    —                 503 + safe error
 * unreadCount()          notificationDb        notificationDb        notificationDb    return {count:0, degraded:true}
 * list()                 notificationDb        notificationDb        notificationDb    empty page (degraded)
 * markAsRead()           notificationCreate    notificationCreate    —                 503 + safe error
 * </pre>
 *
 * <p><b>Routing model</b>: every read query pivots on the authenticated
 * user's userId. A notification is visible to a caller iff its toUserId
 * equals the caller's userId. There are no broadcasts and no role-based
 * fallback — toRole is stored only as a descriptive tag.
 *
 * <p><b>Why different fallbacks?</b>
 * <ul>
 *   <li>Reads (count, list): degrade gracefully — the bell shouldn't crash
 *       the whole UI just because the DB blipped. Frontend renders a dash.</li>
 *   <li>Writes (create, markAsRead): NEVER fake success. We must fail loudly
 *       so the producer can retry / put on a DLQ.</li>
 *   <li>Auth/validation errors are configured under <code>ignoreExceptions</code>
 *       in application.yml — they do NOT trip the breaker.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String CB_DB     = "notificationDb";
    private static final String CB_CREATE = "notificationCreate";

    private final NotificationRepository repository;

    /* ============================================================ */
    /*  CREATE                                                      */
    /* ============================================================ */

    @Override
    @Transactional
    @CircuitBreaker(name = CB_CREATE, fallbackMethod = "createFallback")
    @Retry(name = CB_CREATE)
    public NotificationResponse create(NotificationRequest req) {
        // Producers are services, identified by their own JWT (typically a
        // service-account token). We trust the payload's "to*" fields because
        // only authorised services can reach this endpoint (network policy + JWT).
        log.info("Creating notification eventType={} fromService={} toUserId={} toRole={}",
                req.getEventType(), req.getFromService(),
                req.getToUserId(), req.getToRole());

        Notification entity = Notification.builder()
                .eventType(req.getEventType())
                .message(req.getMessage())
                .fromService(req.getFromService())
                .fromRole(req.getFromRole())
                .fromUserId(req.getFromUserId())
                .toRole(req.getToRole())
                .toUserId(req.getToUserId())
                .referenceId(req.getReferenceId())
                .payload(req.getPayload())
                .isRead(false)
                .build();

        Notification saved = repository.save(entity);
        log.debug("Notification persisted id={} toUserId={}", saved.getId(), saved.getToUserId());
        return NotificationResponse.from(saved);
    }

    /**
     * Fallback for create. Writes MUST NOT silently succeed.
     * We translate to ServiceDegradedException → HTTP 503 so the caller
     * (Feign producer) can retry / dead-letter on its side.
     */
    @SuppressWarnings("unused")
    private NotificationResponse createFallback(NotificationRequest req,
                                                Throwable t) {
        log.error("CREATE fallback invoked for eventType={} cause={}",
                req.getEventType(), t.toString());
        if (t instanceof CallNotPermittedException) {
            throw new ServiceDegradedException(
                    "Notification storage circuit is OPEN — writes rejected.", t);
        }
        throw new ServiceDegradedException(
                "Failed to persist notification after retries.", t);
    }

    /* ============================================================ */
    /*  UNREAD COUNT                                                */
    /* ============================================================ */

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @CircuitBreaker(name = CB_DB, fallbackMethod = "unreadCountFallback")
    @Retry(name = CB_DB)
    @Bulkhead(name = CB_DB)
    public UnreadCountResponse unreadCountForCurrentUser() {
        AuthenticatedUser u = SecurityUtil.currentUser();
        long count = repository.countUnreadForRecipient(u.getUserId());
        return UnreadCountResponse.builder()
                .count(count).degraded(false).build();
    }

    /**
     * Reads degrade gracefully. The bell icon should never crash the page.
     * We return zero + degraded=true so the frontend can render "—" and
     * silently retry on the next poll.
     */
    @SuppressWarnings("unused")
    private UnreadCountResponse unreadCountFallback(Throwable t) {
        log.warn("UNREAD-COUNT fallback engaged: {}", t.toString());
        return UnreadCountResponse.builder().count(0).degraded(true).build();
    }

    /* ============================================================ */
    /*  LIST                                                        */
    /* ============================================================ */

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @CircuitBreaker(name = CB_DB, fallbackMethod = "listFallback")
    @Retry(name = CB_DB)
    @Bulkhead(name = CB_DB)
    public Page<NotificationResponse> listForCurrentUser(String eventType,
                                                         String fromRole,
                                                         Pageable pageable) {
        AuthenticatedUser u = SecurityUtil.currentUser();
        Page<Notification> page = repository.findAllForRecipientFiltered(
                u.getUserId(),
                emptyToNull(eventType),
                emptyToNull(fromRole),
                pageable);
        return page.map(NotificationResponse::from);
    }

    @SuppressWarnings("unused")
    private Page<NotificationResponse> listFallback(String eventType,
                                                    String fromRole,
                                                    Pageable pageable,
                                                    Throwable t) {
        log.warn("LIST fallback engaged: {}", t.toString());
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    /* ============================================================ */
    /*  MARK AS READ                                                */
    /* ============================================================ */

    @Override
    @Transactional
    @CircuitBreaker(name = CB_CREATE, fallbackMethod = "markAsReadFallback")
    @Retry(name = CB_CREATE)
    public NotificationResponse markAsRead(Long id) {
        AuthenticatedUser u = SecurityUtil.currentUser();

        // Single-statement update keeps the operation atomic, idempotent
        // (re-running on an already-read row is a no-op), and avoids
        // a SELECT-then-UPDATE race condition.
        int updated = repository.markAsReadIfOwned(id, u.getUserId());

        if (updated == 0) {
            // Row exists but already read? -> return current state.
            // Row doesn't exist or not owned? -> 404 (don't leak existence).
            Notification existing = repository
                    .findByIdForRecipient(id, u.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Notification not found: " + id));
            return NotificationResponse.from(existing);
        }

        return repository.findByIdForRecipient(id, u.getUserId())
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found after update: " + id));
    }

    @SuppressWarnings("unused")
    private NotificationResponse markAsReadFallback(Long id, Throwable t) {
        log.error("MARK-AS-READ fallback engaged id={} cause={}", id, t.toString());
        if (t instanceof AccessDeniedException ade) throw ade;
        if (t instanceof ResourceNotFoundException rnf) throw rnf;
        if (t instanceof CallNotPermittedException) {
            throw new ServiceDegradedException(
                    "Notification updates are temporarily unavailable.", t);
        }
        if (t instanceof DataAccessException dae) {
            throw new ServiceDegradedException("Storage error.", dae);
        }
        throw new ServiceDegradedException(
                "Could not mark notification as read.", t);
    }

    /* ============================================================ */
    /*  Helpers                                                     */
    /* ============================================================ */

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}