package com.gotogether.trust.entity;

import com.gotogether.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only ledger of every Trust Score change (DB Schema Part 2) — Trust &
 * Discovery Module A: "each update is versioned/timestamped so a Moderator
 * can see the score's trajectory." Extends {@link BaseEntity} directly (own
 * {@code gen_random_uuid()} PK) rather than {@code AuditableEntity} — this
 * table has only {@code created_at}, no {@code updated_at} (rows are never
 * mutated once written).
 */
@Entity
@Table(name = "trust_score_history")
public class TrustScoreHistory extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "old_score", nullable = false, updatable = false)
    private BigDecimal oldScore;

    @Column(name = "new_score", nullable = false, updatable = false)
    private BigDecimal newScore;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "related_review_id", updatable = false)
    private UUID relatedReviewId;

    @Column(name = "related_trip_id", updatable = false)
    private UUID relatedTripId;

    @Column(name = "updated_by", updatable = false)
    private UUID updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TrustScoreHistory() {
        // JPA
    }

    public static TrustScoreHistory record(
            UUID userId, BigDecimal oldScore, BigDecimal newScore, String reason, UUID relatedReviewId, UUID relatedTripId) {
        TrustScoreHistory history = new TrustScoreHistory();
        history.userId = userId;
        history.oldScore = oldScore;
        history.newScore = newScore;
        history.reason = reason;
        history.relatedReviewId = relatedReviewId;
        history.relatedTripId = relatedTripId;
        return history;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getOldScore() {
        return oldScore;
    }

    public BigDecimal getNewScore() {
        return newScore;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRelatedReviewId() {
        return relatedReviewId;
    }

    public UUID getRelatedTripId() {
        return relatedTripId;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
