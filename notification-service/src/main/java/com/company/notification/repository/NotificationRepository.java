package com.company.notification.repository;

import com.company.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RBAC enforcement happens IN the queries, not in service code.
 * That guarantees no caller can ever fetch another user's notifications
 * even if a service-layer bug forgets to filter.
 *
 * Routing rule: a row is visible to user U iff n.toUserId = U.userId.
 * Optional secondary filters: eventType and fromRole.
 */
@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {

    /* ---------------------- Bell icon (hot path) ---------------------- */

    @Query("""
           SELECT COUNT(n)
             FROM Notification n
            WHERE n.toUserId = :userId
              AND n.isRead = false
           """)
    long countUnreadForRecipient(@Param("userId") String userId);

    /* ---------------------- Listing (bell dropdown) ------------------- */

    @Query("""
           SELECT n FROM Notification n
            WHERE n.toUserId = :userId
            ORDER BY n.createdAt DESC
           """)
    Page<Notification> findAllForRecipient(@Param("userId") String userId,
                                           Pageable pageable);

    @Query("""
           SELECT n FROM Notification n
            WHERE n.toUserId = :userId
              AND (:eventType IS NULL OR n.eventType = :eventType)
              AND (:fromRole  IS NULL OR n.fromRole  = :fromRole)
            ORDER BY n.createdAt DESC
           """)
    Page<Notification> findAllForRecipientFiltered(@Param("userId") String userId,
                                                   @Param("eventType") String eventType,
                                                   @Param("fromRole") String fromRole,
                                                   Pageable pageable);

    /* ---------------------- Mark as read (ownership-checked) ---------- */

    /**
     * Atomic, ownership-checked update. Returns the number of rows updated.
     * If 0 → either the notification doesn't exist OR it doesn't belong to
     * this recipient. Either way we 404 — never 403, so we don't leak existence.
     */
    @Modifying
    @Query("""
           UPDATE Notification n
              SET n.isRead = true
            WHERE n.id = :id
              AND n.toUserId = :userId
              AND n.isRead = false
           """)
    int markAsReadIfOwned(@Param("id") Long id,
                          @Param("userId") String userId);

    @Query("""
           SELECT n FROM Notification n
            WHERE n.id = :id
              AND n.toUserId = :userId
           """)
    Optional<Notification> findByIdForRecipient(@Param("id") Long id,
                                                @Param("userId") String userId);
}