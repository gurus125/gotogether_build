package com.gotogether.trust.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serves both {@code GET /users/{id}/trust-score} (API Spec Section 12, the
 * public breakdown — {@code improvementTips} always {@code null} there,
 * matching the "nulls are explicit, never omitted" JSON convention rather
 * than two near-duplicate response shapes) and {@code GET
 * /users/me/trust-score} (Section 4, the self view — populates {@code
 * improvementTips}).
 */
public record TrustScoreResponse(
        BigDecimal currentScore, String level, TrustScoreComponents components, List<String> improvementTips) {
}
