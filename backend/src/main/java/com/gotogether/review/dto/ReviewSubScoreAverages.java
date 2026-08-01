package com.gotogether.review.dto;

/**
 * Rolling average of every Published review's six 1-5 sub-scores naming one
 * user — the {@code trust} module's "Reviews received" component input (40%
 * weight, Business Rules Trust & Discovery Module A). {@code reviewCount == 0}
 * means the user has no Published reviews yet (a brand-new account, or one
 * whose reviews are all still Blind) — {@code TrustService} treats this as
 * "no data," not a zero score, per Module A's "Building/New... not a
 * penalty" framing.
 */
public record ReviewSubScoreAverages(int reviewCount, double averageSubScore) {

    public static final ReviewSubScoreAverages NONE = new ReviewSubScoreAverages(0, 0);
}
