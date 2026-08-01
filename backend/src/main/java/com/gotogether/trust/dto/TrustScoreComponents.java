package com.gotogether.trust.dto;

import java.math.BigDecimal;

/** Component breakdown mirroring {@code trust_scores}' columns (Trust & Discovery Module A's weightage table) — every field nullable since a brand-new user's row has none computed yet (just the seeded 6.5 current_score). */
public record TrustScoreComponents(
        BigDecimal reviews,
        BigDecimal completion,
        BigDecimal verification,
        BigDecimal organizer,
        BigDecimal reportsPenalty,
        BigDecimal accountActivity,
        BigDecimal profileCompleteness) {
}
