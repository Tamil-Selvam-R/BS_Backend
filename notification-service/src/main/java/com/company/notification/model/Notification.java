package com.company.notification.model;

import com.company.notification.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Single notifications table. No JOINs. No business linkage.
 *
 * Routing model (BuildSmart):
 *   • toUserId is REQUIRED. Every notification targets exactly one user.
 *   • toRole is REQUIRED as a descriptive tag — useful for filtering
 *     ("show me all events sent to PROJECT_MANAGER role") and as a sanity
 *     guard so the UI can render correctly even before resolving the user.
 *   • There are no broadcasts. Department fields are gone.
 *
 * Indexes are critical because the bell icon polls every 15-30s. The
 * (toUserId, isRead) composite index makes the per-user unread-count an
 * O(log n) single index seek.
 *
 * referenceId is a generic foreign key (taskId, invoiceId, etc.) — kept as
 * a String so any producer can put any ID format here without coupling.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                // Hot path: bell-icon unread-count for one user.
                @Index(name = "idx_to_user_unread",
                        columnList = "to_user_id, is_read"),
                // Listing the user's notifications by recency.
                @Index(name = "idx_to_user_created",
                        columnList = "to_user_id, created_at"),
                // Filter the user's notifications by event type.
                @Index(name = "idx_to_user_event",
                        columnList = "to_user_id, event_type"),
                // Lookup by reference (e.g. show all notifications for invoice INV-042).
                @Index(name = "idx_reference",
                        columnList = "reference_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Event kind. Stored as String (not @Enumerated) so a producer can send
     * a new event type without forcing a DB migration on consumers.
     */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /**
     * Human-readable text shown in the bell-icon dropdown. Built by the
     * producer at push time and stored verbatim — consumers do not format it.
     */
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    /**
     * The microservice that produced this notification. Useful for filtering
     * and ops debugging.
     */
    @Column(name = "from_service", length = 64)
    private String fromService;

    /**
     * The role of the user that triggered the event. Used by the recipient's
     * UI to display sender context: "Project Manager Priya approved your task".
     */
    @Column(name = "from_role", length = 64)
    private String fromRole;

    /**
     * The userId of the user that triggered the event. Nullable for system-
     * generated events (e.g. cron jobs).
     */
    @Column(name = "from_user_id", length = 64)
    private String fromUserId;

    /**
     * Recipient role — REQUIRED.
     * Acts as a descriptive tag, NOT as the primary routing key. The bell
     * dropdown can filter on this ("show me only events directed at me as a
     * Project Manager"), but a user only ever sees rows where toUserId
     * matches their own userId.
     */
    @Column(name = "to_role", nullable = false, length = 64)
    private String toRole;

    /**
     * Recipient userId — REQUIRED.
     * This is the primary routing key. Only this user sees this notification.
     * Every producer MUST resolve and pass the specific recipient.
     */
    @Column(name = "to_user_id", nullable = false, length = 64)
    private String toUserId;

    /**
     * Generic foreign key — taskId, invoiceId, issueId, etc. The producer
     * decides the format. Used for deep-linking from the bell dropdown into
     * the relevant page.
     */
    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    /**
     * Optional JSON payload for the frontend (deep-link params, icons, etc.).
     * Stored as TEXT/JSON depending on the dialect.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload")
    private String payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Convenience for marking read — keeps the field name change in one place.
     */
    public void markRead() {
        this.isRead = true;
    }

    public static EventType eventTypeOrGeneric(String raw) {
        try {
            return EventType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return EventType.GENERIC;
        }
    }
}