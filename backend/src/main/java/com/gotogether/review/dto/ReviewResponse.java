package com.gotogether.review.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Mirrors {@code review.entity.Review} plus reviewer display info (API Spec Section 11) — {@code highlightedTraits} only ever populated on the {@code GET /users/{id}/reviews} listing (Trust & Discovery Module B's "recurring positive traits... 3+ reviews"), null/empty on a fresh submission. */
public record ReviewResponse(
        UUID id,
        UUID tripId,
        UUID reviewerId,
        String reviewerDisplayName,
        String reviewerPhotoUrl,
        UUID revieweeId,
        short ratingBehaviour,
        short ratingPunctuality,
        short ratingCommunication,
        short ratingCooperation,
        short ratingSafety,
        short ratingReliability,
        short overallRating,
        String comment,
        String status,
        String visibility,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        List<String> highlightedTraits) {
}
