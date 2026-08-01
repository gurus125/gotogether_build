package com.gotogether.trust.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of {@code GET /users/me/trust-score/history} (API Spec Section 12). */
public record TrustScoreHistoryEntry(
        UUID id, BigDecimal oldScore, BigDecimal newScore, String reason, UUID relatedReviewId,
        UUID relatedTripId, OffsetDateTime createdAt) {
}
