package com.gotogether.user.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * Full history of every verification attempt (DB Schema Part 1) — a history
 * table, not a single-row-per-user status, since reverification and
 * rejection-then-retry both need prior attempts kept for audit.
 */
@Entity
@Table(name = "verifications")
public class Verification extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "verification_type")
    private VerificationType type;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "verification_status")
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "document_reference_hash")
    private String documentReferenceHash;

    @Column(name = "document_image_url")
    private String documentImageUrl;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "rejection_reason", columnDefinition = "rejection_reason")
    private RejectionReason rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected Verification() {
        // JPA
    }

    public static Verification submit(User user, VerificationType type, String documentType,
                                       String documentReferenceHash, String documentImageUrl) {
        Verification v = new Verification();
        v.user = user;
        v.type = type;
        v.documentType = documentType;
        v.documentReferenceHash = documentReferenceHash;
        v.documentImageUrl = documentImageUrl;
        return v;
    }

    /** Phone/email verification is auto-approved on OTP/link confirmation — no document review needed. */
    public static Verification autoApprove(User user, VerificationType type) {
        Verification v = new Verification();
        v.user = user;
        v.type = type;
        v.status = VerificationStatus.APPROVED;
        v.reviewedAt = OffsetDateTime.now();
        return v;
    }

    public User getUser() {
        return user;
    }

    public VerificationType getType() {
        return type;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentImageUrl() {
        return documentImageUrl;
    }

    public void approve(UUID reviewerId) {
        this.status = VerificationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = OffsetDateTime.now();
    }

    public void reject(UUID reviewerId, RejectionReason reason) {
        this.status = VerificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedBy = reviewerId;
        this.reviewedAt = OffsetDateTime.now();
    }
}
