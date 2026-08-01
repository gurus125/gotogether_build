package com.gotogether.analytics.entity;

import com.gotogether.common.entity.BaseEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only product event stream (DB Schema Part 3). Extends {@link
 * BaseEntity}, not {@link com.gotogether.common.entity.AuditableEntity} —
 * {@code analytics_events} has only {@code occurred_at}, no {@code
 * updated_at} (rows are never updated once written, same "write-once"
 * convention as {@code audit_logs} — see that entity's own doc).
 *
 * <p>{@code entityType} is a plain {@code String} (loose reference), same
 * convention as {@code notifications.entity_type}/{@code audit_logs.entity_type}
 * — see {@code common.ReferencedEntityType}'s doc.
 */
@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent extends BaseEntity {

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "event_type", nullable = false, updatable = false, columnDefinition = "analytics_event_type")
    private AnalyticsEventType eventType;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    /** JSON, not a typed collection — see {@code company.entity.CompanyVerification#submittedDocuments}'s doc for why {@code @JdbcTypeCode(SqlTypes.JSON)} on a plain {@code String} is this codebase's established JSONB-mapping pattern. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private String metadataJson = "{}";

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected AnalyticsEvent() {
        // JPA
    }

    public static AnalyticsEvent of(AnalyticsEventType eventType, UUID userId, String entityType, UUID entityId, String metadataJson) {
        AnalyticsEvent event = new AnalyticsEvent();
        event.eventType = eventType;
        event.userId = userId;
        event.entityType = entityType;
        event.entityId = entityId;
        event.metadataJson = metadataJson == null ? "{}" : metadataJson;
        return event;
    }

    public AnalyticsEventType getEventType() {
        return eventType;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
