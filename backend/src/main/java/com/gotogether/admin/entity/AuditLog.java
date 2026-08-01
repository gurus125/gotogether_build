package com.gotogether.admin.entity;

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
 * Full history of every Moderator/Admin action (DB Schema Part 3, Operations
 * Module C: "Audit logs — full history of every Moderator/Admin action").
 * Extends {@link BaseEntity}, not {@link com.gotogether.common.entity.AuditableEntity}
 * — the {@code audit_logs} table has only {@code created_at}, no {@code
 * updated_at} (DB Schema Part 3's own note: "the one table in the entire
 * schema with no application code path that writes to it more than once per
 * row" — rows here are append-only, never updated after insert).
 *
 * <p>{@code entityType} is a plain {@code String} (loose reference, like
 * {@code notifications.entity_type}), not {@code report.entity.ReportEntityType}
 * — {@code audit_logs} rows target a much wider variety of things (users,
 * trips, reviews, companies, verifications, trust scores, roles) than
 * Reports' fixed five, and cross-module entity access is forbidden anyway
 * ({@code ArchitectureTest}), so this mirrors {@code common.ReferencedEntityType}'s
 * "loose TEXT, validated at the call site, not by a DB enum" convention
 * rather than reusing another module's stricter enum.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "action", nullable = false, updatable = false, columnDefinition = "audit_action")
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    /** JSON, not a typed collection — see {@code company.entity.CompanyVerification#submittedDocuments}'s doc for why {@code @JdbcTypeCode(SqlTypes.JSON)} on a plain {@code String} is this codebase's established JSONB-mapping pattern. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private String oldValueJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValueJson;

    /**
     * Always {@code null} in this pass — no {@code HttpServletRequest}
     * capture is wired into any controller yet, so nothing ever sets this.
     * The DB column was originally native Postgres {@code INET} (V4
     * migration), which this class's own earlier doc comment flagged as a
     * future mismatch risk for whoever wires real IP capture — but Hibernate's
     * schema *validation* (`ddl-auto: validate`) actually checks column-type
     * compatibility at boot regardless of whether the column is ever written
     * to, so this broke `mvn spring-boot:run` immediately, not just later
     * (caught 2026-07-29 on the user's first real run against a live
     * Postgres instance — this sandbox has no DB to catch it against). Fixed
     * by {@code V7__fix_audit_logs_ip_address_type.sql}, which alters the
     * column to {@code TEXT} — the same "loose plain-String" convention
     * already used for {@link #deviceInfo} on this exact table. Revisit as a
     * real {@code INET} mapping (a custom Hibernate UserType/JdbcType, same
     * category of work as the JSONB pattern) only once IP capture is
     * actually implemented.
     */
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
        // JPA
    }

    /** {@code reasonOrNotes} is folded into {@code new_value} as {@code {"reason": "..."}} rather than given its own column — see this class's doc for why a per-action reason column doesn't exist on the entities this logs about ({@code Trip}, {@code User}, etc.) either; the audit row is the one place the rationale is guaranteed to be recorded. */
    public static AuditLog record(UUID actorId, AuditAction action, String entityType, UUID entityId, String oldValueJson, String newValueJson) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        log.oldValueJson = oldValueJson;
        log.newValueJson = newValueJson;
        return log;
    }

    public UUID getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getOldValueJson() {
        return oldValueJson;
    }

    public String getNewValueJson() {
        return newValueJson;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
