package com.gotogether.report.entity;

import com.gotogether.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Metadata-only supporting evidence attached to a {@link Report} (DB Schema
 * Part 3) — the actual file bytes live in object storage under {@code
 * storageKey}; this row never holds binary content. Deliberately extends
 * {@link BaseEntity} rather than {@link com.gotogether.common.entity.AuditableEntity}:
 * the {@code report_evidence} table has only {@code created_at}, no {@code
 * updated_at} (DB Schema Part 3 — evidence rows are append-only, never
 * edited once uploaded).
 */
@Entity
@Table(name = "report_evidence")
public class ReportEvidence extends BaseEntity {

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "storage_key", nullable = false, updatable = false, unique = true)
    private String storageKey;

    @Column(name = "mime_type", updatable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", updatable = false)
    private Integer fileSizeBytes;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.OffsetDateTime createdAt;

    protected ReportEvidence() {
        // JPA
    }

    /** {@code POST /reports/{id}/evidence} (API Spec Section 15). {@code storageKey} is caller-supplied (Phase 7's {@code CompanyApplyScreen} established the same "manual document-reference field, no real upload endpoint exists yet" convention — see that screen's own class doc). */
    public static ReportEvidence attach(UUID reportId, String storageKey, String mimeType, Integer fileSizeBytes, UUID uploadedBy) {
        ReportEvidence evidence = new ReportEvidence();
        evidence.reportId = reportId;
        evidence.storageKey = storageKey;
        evidence.mimeType = mimeType;
        evidence.fileSizeBytes = fileSizeBytes;
        evidence.uploadedBy = uploadedBy;
        return evidence;
    }

    public UUID getReportId() {
        return reportId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Integer getFileSizeBytes() {
        return fileSizeBytes;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public java.time.OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
