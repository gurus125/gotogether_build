package com.gotogether.company.entity;

import com.gotogether.common.entity.AuditableEntity;
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
 * History of every business-verification attempt (DB Schema Part 3 Section 2)
 * — structurally parallel to {@code user.entity.Verification} but for
 * businesses. A row reaching {@link CompanyVerificationStatus#APPROVED} is
 * what flips the parent {@code TravelCompany}'s status to {@link
 * CompanyStatus#VERIFIED} — see {@code CompanyService}'s class doc for why
 * that approval action has no real endpoint in this pass.
 *
 * <p>{@code submittedDocuments} is plain {@code String} JSON (a JSONB column,
 * not mapped to a Java collection type) — the array-of-{@code
 * {document_type, storage_key}} shape is read/written whole, never queried by
 * individual element, so a typed collection mapping would be pure overhead.
 */
@Entity
@Table(name = "company_verifications")
public class CompanyVerification extends AuditableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    /**
     * Hibernate 6's built-in {@code @JdbcTypeCode(SqlTypes.JSON)} (no extra
     * library needed) rather than a plain {@code columnDefinition = "jsonb"}
     * on a {@code String} — the latter binds as {@code VARCHAR} by default and
     * fails at insert time with "column is of type jsonb but expression is of
     * type character varying." This is the first JSONB column mapped in this
     * codebase; use this same annotation for any future one.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_documents", nullable = false)
    private String submittedDocuments = "[]";

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "company_verification_status")
    private CompanyVerificationStatus status = CompanyVerificationStatus.UNDER_REVIEW;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "decision_notes")
    private String decisionNotes;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "is_reverification", nullable = false)
    private boolean reverification = false;

    protected CompanyVerification() {
        // JPA
    }

    /** The initial verification attempt, created atomically with {@link TravelCompany#apply}. */
    public static CompanyVerification initial(UUID companyId, String submittedDocumentsJson) {
        CompanyVerification v = new CompanyVerification();
        v.companyId = companyId;
        v.submittedDocuments = submittedDocumentsJson == null ? "[]" : submittedDocumentsJson;
        return v;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getSubmittedDocuments() {
        return submittedDocuments;
    }

    public CompanyVerificationStatus getStatus() {
        return status;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public boolean isReverification() {
        return reverification;
    }

    /** {@code POST /admin/companies/{id}/verify} (Phase 8) — the decision that also flips the parent {@link TravelCompany#verify}, composed at {@code CompanyService} (both rows belong to this module). */
    public void approve(UUID reviewerId, String notes) {
        this.status = CompanyVerificationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.decisionNotes = notes;
        this.approvedAt = OffsetDateTime.now();
    }

    public void reject(UUID reviewerId, String notes) {
        this.status = CompanyVerificationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.decisionNotes = notes;
    }
}
