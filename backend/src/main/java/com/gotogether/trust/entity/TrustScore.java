package com.gotogether.trust.entity;

import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * Single current-state row per user (DB Schema Part 2) — a materialized,
 * fast-read snapshot, never written directly except by {@code
 * TrustService}'s recalculation. Deliberately does <b>not</b> extend {@link
 * com.gotogether.common.entity.BaseEntity}/{@code AuditableEntity}, unlike
 * every other entity in this codebase: {@code trust_scores.user_id} is
 * simultaneously the table's PK and its FK to {@code users} (a true 1:1
 * owned-key relationship, DB Schema Part 2), so there is no separate
 * app-generated {@code id} column and no {@code created_at} — only {@code
 * last_calculated_at}. This is a deliberate, schema-driven deviation from the
 * established base-entity convention, not an oversight.
 */
@Entity
@Table(name = "trust_scores")
public class TrustScore {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "current_score", nullable = false)
    private BigDecimal currentScore;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "level", nullable = false, columnDefinition = "trust_level")
    private TrustLevel level = TrustLevel.BUILDING;

    @Column(name = "reviews_component")
    private BigDecimal reviewsComponent;

    @Column(name = "completion_component")
    private BigDecimal completionComponent;

    @Column(name = "verification_component")
    private BigDecimal verificationComponent;

    @Column(name = "organizer_component")
    private BigDecimal organizerComponent;

    @Column(name = "reports_penalty", nullable = false)
    private BigDecimal reportsPenalty = BigDecimal.ZERO;

    @Column(name = "account_activity_component")
    private BigDecimal accountActivityComponent;

    @Column(name = "profile_completeness_component")
    private BigDecimal profileCompletenessComponent;

    @Column(name = "is_frozen", nullable = false)
    private boolean frozen;

    @Column(name = "manual_override_by")
    private UUID manualOverrideBy;

    @Column(name = "manual_override_reason")
    private String manualOverrideReason;

    @Column(name = "last_calculated_at", nullable = false)
    private OffsetDateTime lastCalculatedAt = OffsetDateTime.now();

    protected TrustScore() {
        // JPA
    }

    /** Trust & Discovery Module A: "a brand-new Verified user starts at 6.5" — and per API Spec Section 3, every new signup seeds this row immediately, regardless of verification level yet reached. */
    public static TrustScore seedFor(UUID userId) {
        TrustScore score = new TrustScore();
        score.userId = userId;
        score.currentScore = new BigDecimal("6.5");
        score.level = TrustLevel.BUILDING;
        score.reportsPenalty = BigDecimal.ZERO;
        score.lastCalculatedAt = OffsetDateTime.now();
        return score;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getCurrentScore() {
        return currentScore;
    }

    public TrustLevel getLevel() {
        return level;
    }

    public BigDecimal getReviewsComponent() {
        return reviewsComponent;
    }

    public BigDecimal getCompletionComponent() {
        return completionComponent;
    }

    public BigDecimal getVerificationComponent() {
        return verificationComponent;
    }

    public BigDecimal getOrganizerComponent() {
        return organizerComponent;
    }

    public BigDecimal getReportsPenalty() {
        return reportsPenalty;
    }

    public BigDecimal getAccountActivityComponent() {
        return accountActivityComponent;
    }

    public BigDecimal getProfileCompletenessComponent() {
        return profileCompletenessComponent;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public UUID getManualOverrideBy() {
        return manualOverrideBy;
    }

    public String getManualOverrideReason() {
        return manualOverrideReason;
    }

    public OffsetDateTime getLastCalculatedAt() {
        return lastCalculatedAt;
    }

    /**
     * Applies a freshly-computed composite score (Chapter 3 Section 3.8's
     * normal path). Does nothing to {@code is_frozen} — a Moderator/Admin
     * action is the only way to clear a freeze (not modeled yet, since no
     * Admin endpoint exists for it — see {@code TrustService}'s class doc).
     */
    public void apply(
            BigDecimal newScore, BigDecimal reviews, BigDecimal completion, BigDecimal verification,
            BigDecimal organizer, BigDecimal accountActivity, BigDecimal profileCompleteness) {
        this.currentScore = newScore;
        this.level = TrustLevel.forScore(newScore.doubleValue());
        this.reviewsComponent = reviews;
        this.completionComponent = completion;
        this.verificationComponent = verification;
        this.organizerComponent = organizer;
        this.accountActivityComponent = accountActivity;
        this.profileCompletenessComponent = profileCompleteness;
        this.lastCalculatedAt = OffsetDateTime.now();
    }

    /** Chapter 3 Section 3.8's Manual Review branch: "score frozen pending review" — the composite score is computed but withheld, not applied. */
    public void freeze() {
        this.frozen = true;
        this.lastCalculatedAt = OffsetDateTime.now();
    }

    /**
     * {@code POST /admin/users/{id}/trust-score/unfreeze}-equivalent (Phase
     * 8, Operations Module C: "Admin view+freeze/unfreeze on anomaly" —
     * {@code AdminService} enforces the {@code ADMIN}-only gate, not here).
     * Clears the freeze so the next trigger recalculates normally; {@code
     * TrustService}'s own class doc named this as one of the two things
     * blocked pending this module.
     */
    public void unfreeze(UUID adminId, String reason) {
        this.frozen = false;
        this.manualOverrideBy = adminId;
        this.manualOverrideReason = reason;
    }

    /**
     * The {@code Reports & safety violations} component (Trust &amp;
     * Discovery Module A, -15% weight) — {@code TrustService}'s class doc
     * flagged this as "always 0" pending this module. Stored as a running,
     * always-<= 0 adjustment (see {@code TrustService#recalculate}'s
     * comment: "reports penalty is stored <= 0, so this subtracts"), floored
     * at {@code -3.0} so a long run of substantiated reports can't push the
     * composite arbitrarily negative from this one component alone — a
     * reasonable, undocumented-elsewhere default (same "adopted here since
     * never formally resolved" precedent as {@code ANOMALY_THRESHOLD}).
     */
    public void adjustReportsPenalty(BigDecimal delta) {
        BigDecimal floor = new BigDecimal("-3.0");
        BigDecimal adjusted = this.reportsPenalty.add(delta);
        this.reportsPenalty = adjusted.compareTo(floor) < 0 ? floor : adjusted;
    }
}
