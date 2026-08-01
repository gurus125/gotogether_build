package com.gotogether.report.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * A single Report/Response record (DB Schema Part 3, Operations Module B).
 * Extends {@link AuditableEntity} like almost every other mutable table in
 * the schema (Part 1 Section 1's blanket rule — {@code audit_logs} and
 * {@code analytics_events} are the schema's only two documented exceptions,
 * being genuinely append-only; see {@code AuditLog}'s doc). This class
 * previously carried a doc comment claiming reports had "no updated_at" —
 * that was simply wrong, and the {@code reports} table was missing the
 * column entirely until {@code V8__add_reports_updated_at.sql} (found when
 * the user's first real `mvn spring-boot:run` failed Hibernate's schema
 * validation; this sandbox has no DB to catch it against beforehand).
 */
@Entity
@Table(name = "reports")
public class Report extends AuditableEntity {

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "entity_type", nullable = false, updatable = false, columnDefinition = "report_entity_type")
    private ReportEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "report_reason")
    private ReportReason reason;

    @Column(name = "details", updatable = false)
    private String details;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "report_status")
    private ReportStatus status = ReportStatus.OPEN;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "priority", nullable = false, columnDefinition = "report_priority")
    private ReportPriority priority = ReportPriority.ROUTINE;

    @Column(name = "assigned_moderator_id")
    private UUID assignedModeratorId;

    @Column(name = "resolution")
    private String resolution;

    /** See {@link ReportResolutionAction}'s own doc for why this has no {@code @JdbcType(NativeEnumJdbcType.class)}. */
    @Column(name = "resolution_action")
    private ReportResolutionAction resolutionAction;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected Report() {
        // JPA
    }

    /** {@code POST /reports} / {@code POST /reports/emergency} (API Spec Section 15) — {@code priority} is {@link ReportPriority#ROUTINE} for the former, forced to {@link ReportPriority#EMERGENCY} for the latter (see {@code ReportService#fileEmergencyReport}). */
    public static Report file(UUID reporterId, ReportEntityType entityType, UUID entityId, ReportReason reason, String details, ReportPriority priority) {
        Report report = new Report();
        report.reporterId = reporterId;
        report.entityType = entityType;
        report.entityId = entityId;
        report.reason = reason;
        report.details = details;
        report.priority = priority;
        return report;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public ReportEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public ReportPriority getPriority() {
        return priority;
    }

    public UUID getAssignedModeratorId() {
        return assignedModeratorId;
    }

    public String getResolution() {
        return resolution;
    }

    public ReportResolutionAction getResolutionAction() {
        return resolutionAction;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public boolean isOpenOrInReview() {
        return status == ReportStatus.OPEN || status == ReportStatus.IN_REVIEW;
    }

    /** Assignment is a lightweight side effect of a Moderator/Admin opening the queue item — not a distinct status transition (Operations Module B's Moderator Workflow doesn't model "claimed" as its own state). */
    public void assignTo(UUID moderatorId) {
        this.assignedModeratorId = moderatorId;
        if (this.status == ReportStatus.OPEN) {
            this.status = ReportStatus.IN_REVIEW;
        }
    }

    /**
     * {@code POST /admin/reports/{id}/resolve} (API Spec Section 16). {@code
     * DISMISSED} resolves to {@link ReportStatus#DISMISSED} (Business Rules
     * Module B: "an unsubstantiated report never touches the [Trust] score");
     * every other action resolves to {@link ReportStatus#RESOLVED} — the
     * distinction between "found nothing" and "found something and acted"
     * matters enough downstream (Trust Score's Reports penalty) to be its own
     * status rather than folded into one generic "closed" value.
     */
    public void resolve(UUID reviewerId, ReportResolutionAction action, String resolutionNotes) {
        this.assignedModeratorId = reviewerId;
        this.resolutionAction = action;
        this.resolution = resolutionNotes;
        this.status = action == ReportResolutionAction.DISMISSED ? ReportStatus.DISMISSED : ReportStatus.RESOLVED;
        this.resolvedAt = OffsetDateTime.now();
    }
}
