package com.gotogether.review.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * A single directional, double-blind peer trust record (DB Schema Part 2,
 * Trust & Discovery Module B). One row exists per (trip, reviewer, reviewee)
 * triple — the DB's {@code ux_reviews_trip_reviewer_reviewee} unique
 * constraint is the real backstop against duplicates; {@link
 * com.gotogether.review.service.ReviewService#submit} checks it first for a
 * clean 409 rather than relying on the constraint violation.
 */
@Entity
@Table(name = "reviews")
public class Review extends AuditableEntity {

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Column(name = "reviewee_id", nullable = false, updatable = false)
    private UUID revieweeId;

    @Column(name = "rating_behaviour", nullable = false, updatable = false)
    private short ratingBehaviour;

    @Column(name = "rating_punctuality", nullable = false, updatable = false)
    private short ratingPunctuality;

    @Column(name = "rating_communication", nullable = false, updatable = false)
    private short ratingCommunication;

    @Column(name = "rating_cooperation", nullable = false, updatable = false)
    private short ratingCooperation;

    @Column(name = "rating_safety", nullable = false, updatable = false)
    private short ratingSafety;

    @Column(name = "rating_reliability", nullable = false, updatable = false)
    private short ratingReliability;

    @Column(name = "overall_rating", nullable = false, updatable = false)
    private short overallRating;

    @Column(name = "comment", updatable = false)
    private String comment;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "review_status")
    private ReviewStatus status = ReviewStatus.SUBMITTED;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "visibility", nullable = false, columnDefinition = "review_visibility")
    private ReviewVisibility visibility = ReviewVisibility.BLIND;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "moderation_notes")
    private String moderationNotes;

    protected Review() {
        // JPA
    }

    public static Review submit(
            UUID tripId, UUID reviewerId, UUID revieweeId, short behaviour, short punctuality, short communication,
            short cooperation, short safety, short reliability, short overall, String comment) {
        Review review = new Review();
        review.tripId = tripId;
        review.reviewerId = reviewerId;
        review.revieweeId = revieweeId;
        review.ratingBehaviour = behaviour;
        review.ratingPunctuality = punctuality;
        review.ratingCommunication = communication;
        review.ratingCooperation = cooperation;
        review.ratingSafety = safety;
        review.ratingReliability = reliability;
        review.overallRating = overall;
        review.comment = comment;
        return review;
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public UUID getRevieweeId() {
        return revieweeId;
    }

    public short getRatingBehaviour() {
        return ratingBehaviour;
    }

    public short getRatingPunctuality() {
        return ratingPunctuality;
    }

    public short getRatingCommunication() {
        return ratingCommunication;
    }

    public short getRatingCooperation() {
        return ratingCooperation;
    }

    public short getRatingSafety() {
        return ratingSafety;
    }

    public short getRatingReliability() {
        return ratingReliability;
    }

    public short getOverallRating() {
        return overallRating;
    }

    public String getComment() {
        return comment;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public ReviewVisibility getVisibility() {
        return visibility;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getModerationNotes() {
        return moderationNotes;
    }

    /** {@code Submitted -> Published}: both directions submitted, or the 14-day window closed with only this side in. */
    public void publish() {
        this.status = ReviewStatus.PUBLISHED;
        this.visibility = ReviewVisibility.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
    }

    public boolean isBlindAndSubmitted() {
        return status == ReviewStatus.SUBMITTED && visibility == ReviewVisibility.BLIND;
    }

    /**
     * {@code POST /admin/reports/{id}/resolve} with {@code
     * resolution_action=content_removed} against a {@code review}-typed
     * report (Phase 8, Operations Module C's Content moderation capability:
     * "remove/hide... reviews... Moderator+Admin"). {@code notes} is stored
     * in the pre-existing {@code moderation_notes} column, which had no
     * writer anywhere in the codebase until now.
     */
    public void hide(String notes) {
        this.status = ReviewStatus.HIDDEN;
        this.moderationNotes = notes;
    }

    /** Same trigger as {@link #hide}, terminal instead of reversible (Operations Module C's "remove" vs "hide" distinction). */
    public void remove(String notes) {
        this.status = ReviewStatus.REMOVED;
        this.moderationNotes = notes;
    }

    /** Average of the six 1-5 sub-scores for this single review (used to feed the {@code trust} module's rolling reviews component). */
    public double averageSubScore() {
        return (ratingBehaviour + ratingPunctuality + ratingCommunication + ratingCooperation + ratingSafety + ratingReliability) / 6.0;
    }
}
