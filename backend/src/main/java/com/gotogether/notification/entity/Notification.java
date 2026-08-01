package com.gotogether.notification.entity;

import com.gotogether.common.entity.BaseEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * One in-app notification row (DB Schema Part 2) — "the durable source of
 * truth regardless of push delivery success" (this table's own migration
 * comment). Does not extend {@link com.gotogether.common.entity.AuditableEntity}
 * — the {@code notifications} table has {@code created_at} only, no {@code
 * updated_at}/trigger, so this declares its own DB-defaulted, insert-only
 * {@code createdAt} directly (same shape decision as {@code
 * trust.entity.TrustScoreHistory}).
 *
 * <p>Chapter 3 Section 3.9's Notification Lifecycle is {@code Generated ->
 * Queued -> Delivered|Failed (retry up to 3x) -> Read|Dismissed -> Archived}.
 * This pass only ever creates a row already {@link NotificationStatus#DELIVERED}
 * — see {@code NotificationService}'s class doc for why the push-queue hops
 * are skipped rather than simulated (no FCM project or device-token table
 * exists yet; in-app delivery is instant and real, which the migration
 * comment above already calls the actual source of truth anyway).
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "notification_type")
    private NotificationType type;

    /** One of {@code common.ReferencedEntityType}'s table names, or {@code null} for a type with no single target (e.g. {@code ANNOUNCEMENT}). */
    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "body", updatable = false)
    private String body;

    /** {@code low}/{@code medium}/{@code high} (DB CHECK constraint) — plain {@code String}, not an enum, matching the table's own loose typing. */
    @Column(name = "priority", nullable = false, updatable = false)
    private String priority = "medium";

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "notification_status")
    private NotificationStatus status = NotificationStatus.GENERATED;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "dismissed_at")
    private OffsetDateTime dismissedAt;

    @Column(name = "delivery_attempts", nullable = false)
    private short deliveryAttempts = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
        // JPA
    }

    public static Notification create(
            UUID recipientId, UUID actorId, NotificationType type, String entityType, UUID entityId, String title,
            String body, String priority) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.actorId = actorId;
        n.type = type;
        n.entityType = entityType;
        n.entityId = entityId;
        n.title = title;
        n.body = body;
        n.priority = priority != null ? priority : "medium";
        // Delivered immediately — see this class's doc on why the push-queue
        // hops aren't simulated.
        n.status = NotificationStatus.DELIVERED;
        return n;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getPriority() {
        return priority;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getDismissedAt() {
        return dismissedAt;
    }

    public short getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** {@code Delivered -> Read}: user opens/views it (Chapter 3 Section 3.9). No-op if already Read/Archived. */
    public void markRead() {
        if (status == NotificationStatus.READ || status == NotificationStatus.ARCHIVED) {
            return;
        }
        status = NotificationStatus.READ;
        readAt = OffsetDateTime.now();
    }

    /** {@code Read|Delivered -> Archived}: `POST /notifications/read-all`'s "archives Read/Delivered, not queued". */
    public void archive() {
        status = NotificationStatus.ARCHIVED;
    }

    public boolean isArchivable() {
        return status == NotificationStatus.DELIVERED || status == NotificationStatus.READ;
    }
}
