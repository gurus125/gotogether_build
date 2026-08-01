package com.gotogether.trust.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.joinrequest.dto.OrganizerReliabilityStats;
import com.gotogether.joinrequest.service.JoinRequestService;
import com.gotogether.membership.dto.MembershipCompletionStats;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.review.dto.ReviewSubScoreAverages;
import com.gotogether.review.service.ReviewService;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.dto.TrustScoreComponents;
import com.gotogether.trust.dto.TrustScoreHistoryEntry;
import com.gotogether.trust.dto.TrustScoreResponse;
import com.gotogether.trust.entity.TrustLevel;
import com.gotogether.trust.entity.TrustScore;
import com.gotogether.trust.entity.TrustScoreHistory;
import com.gotogether.trust.repository.TrustScoreHistoryRepository;
import com.gotogether.trust.repository.TrustScoreRepository;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The trust module's only entry point for other modules — everything else
 * ({@code trust_scores}/{@code trust_score_history} entities/repositories) is
 * package-private to this module in practice (enforced by {@code
 * ArchitectureTest}).
 *
 * <p>Depends one-directionally on {@code review} (Reviews component), {@code
 * membership} (Trip completion behaviour component), {@code user}
 * (Verification level component, account age), {@code trip} (organizer's own
 * trip ids, for the Organizer reliability component), {@code joinrequest}
 * (Organizer reliability's response-time/accept-rate data), and {@code
 * profile} (Profile completeness component) — never the reverse. This is
 * deliberately the most "downstream" module in the system: every trigger
 * that should recalculate a Trust Score ({@code review} reaching Published,
 * {@code trip}/{@code membership} reaching Completed/Cancelled) is wired at
 * the <b>controller layer</b> that already calls both sides ({@code
 * ReviewController}, {@code TripController}, {@code MembershipController}),
 * never from inside {@code ReviewService}/{@code TripService}/{@code
 * MembershipService} themselves — because this module reads all of them,
 * any of them depending back on {@code trust} would create a cycle. See
 * {@code ReviewService}'s class doc for the identical reasoning already
 * established for {@code chat} in Phase 4.
 *
 * <p>Every user gets a {@code trust_scores} row lazily seeded at 6.5/Building
 * the first time anything asks for it ({@link #ensureRow}) — mirroring the
 * exact lazy-row-creation convention already used twice in this codebase
 * ({@code ChatService#ensureRoomAndOrganizerSeat}, {@code
 * MembershipService#ensureOrganizerSeat}). The API Specification (Section 3)
 * describes this row being seeded synchronously at signup instead; lazy
 * creation here produces the identical end state without requiring {@code
 * user}/{@code auth} to depend on {@code trust} (which would also cycle).
 *
 * <p><b>As of Phase 8</b>, the {@code Reports & safety violations} component
 * (-15% weight) is wired: {@code admin.service.AdminService} calls {@link
 * #applyReportsPenalty} whenever a report resolves to a real enforcement
 * action, and {@link #unfreeze} lets an Admin clear an anomaly-detected
 * freeze. Before Phase 8 this component was permanently {@code 0} and both
 * methods didn't exist — noted here since a lot of this class's reasoning
 * below was written against that earlier, more limited state. {@code
 * Account age & activity consistency} (10%) is computed from account age
 * alone; the "activity consistency" half has no data source in this schema
 * (no login/session-history table exists — {@code users.last_login_at} is a
 * single timestamp, not a series), so it's a real, unflagged documentation
 * gap rather than something silently invented here. Verification-lifecycle-
 * triggered recalculation (the fourth trigger Module A names) isn't wired
 * either, since the only verification transition currently reachable
 * (auto phone/email on signup) happens before any trust-relevant event could
 * exist anyway, and the Government-ID moderator-approval endpoint that would
 * make this trigger meaningful is itself Phase 8 (see {@code
 * UserService#recordAutoVerification}'s doc); the current verification level
 * is still read correctly on every recalculation, just not the instant it
 * changes. A full manual Trust Score override (Admin directly setting a
 * number) is still not modeled — only {@link #unfreeze} (clear the freeze,
 * let the normal formula run again) and {@link #applyReportsPenalty} (the
 * one component Admin action can actually move) exist; API Specification
 * Section 16 doesn't list a "directly edit a score" endpoint either
 * ("even Administrators cannot directly edit a Trust Score," Operations
 * Module C), so this is a documented absence, not a gap.
 */
@Service
public class TrustService {

    /** Trust & Discovery Module A's weightage table. */
    private static final BigDecimal WEIGHT_REVIEWS = new BigDecimal("0.40");
    private static final BigDecimal WEIGHT_COMPLETION = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_VERIFICATION = new BigDecimal("0.15");
    private static final BigDecimal WEIGHT_ORGANIZER = new BigDecimal("0.10");
    private static final BigDecimal WEIGHT_ACCOUNT_ACTIVITY = new BigDecimal("0.10");
    private static final BigDecimal WEIGHT_PROFILE_COMPLETENESS = new BigDecimal("0.05");

    /** Neutral placeholder for a component with no history yet (Module A: "Building/New... not a penalty") — same value as the seeded starting score. */
    private static final BigDecimal NEUTRAL_NO_DATA = new BigDecimal("6.5");

    /** Module A's own example: "sudden spike... e.g. >2-point single-review swing" (Chapter 3 Open Question 1's suggested default, adopted here since it was never formally resolved either way). */
    private static final BigDecimal ANOMALY_THRESHOLD = new BigDecimal("2.5");

    private static final double ACCOUNT_AGE_MAX_DAYS = 730; // 2 years -> full marks
    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final TrustScoreRepository trustScoreRepository;
    private final TrustScoreHistoryRepository trustScoreHistoryRepository;
    private final ReviewService reviewService;
    private final MembershipService membershipService;
    private final UserService userService;
    private final TripService tripService;
    private final JoinRequestService joinRequestService;
    private final ProfileService profileService;

    public TrustService(
            TrustScoreRepository trustScoreRepository, TrustScoreHistoryRepository trustScoreHistoryRepository,
            ReviewService reviewService, MembershipService membershipService, UserService userService,
            TripService tripService, JoinRequestService joinRequestService, ProfileService profileService) {
        this.trustScoreRepository = trustScoreRepository;
        this.trustScoreHistoryRepository = trustScoreHistoryRepository;
        this.reviewService = reviewService;
        this.membershipService = membershipService;
        this.userService = userService;
        this.tripService = tripService;
        this.joinRequestService = joinRequestService;
        this.profileService = profileService;
    }

    // --- cross-module entry points (called from controller-layer composition) ---

    /** Trigger 1 of 2 (Chapter 3 Section 3.8): a Review reaching Published. Called from {@code ReviewController} right after {@code ReviewService#submit} reports a pair just crossed into Published. */
    @Transactional
    public void recalculateForReviewPublished(UUID userId) {
        recalculate(userId, "Review published", null, null);
    }

    /** Trigger 2 of 2: a Trip reaching Completed — applies to every participant (Trip completion behaviour affects everyone, not just the Organizer). Called from {@code MembershipController#complete}. */
    @Transactional
    public void recalculateForTripCompleted(UUID tripId, UUID userId) {
        recalculate(userId, "Trip completed", null, tripId);
    }

    /**
     * Trip Cancellation "impacts Organizer's Trust Score (Chapter 3 Section
     * 3.8), not Members'" (Chapter 3 Section 3.2's own edge-case note) —
     * deliberately takes only the organizer id, not the full roster. Called
     * from {@code TripController#cancel}.
     */
    @Transactional
    public void recalculateForTripCancelled(UUID tripId, UUID organizerId) {
        recalculate(organizerId, "Trip cancelled", null, tripId);
    }

    // --- Phase 8 admin entry points (called by admin.service.AdminService — see ReportService's class doc for why this composition lives in admin) ---

    /** Clears an anomaly-detected freeze and forces an immediate recalculation (Operations Module C: "Admin view+freeze/unfreeze on anomaly"). This class's own doc named unfreezing as one of the two things blocked pending the {@code admin} module — no longer true as of Phase 8. */
    @Transactional
    public void unfreeze(UUID userId, UUID adminId, String reason) {
        TrustScore trust = ensureRow(userId);
        trust.unfreeze(adminId, reason);
        trustScoreRepository.save(trust);
        recalculate(userId, "Unfrozen by admin: " + reason, null, null);
    }

    /**
     * Applies the {@code Reports & safety violations} component (the other
     * thing this class's doc named as "always 0" pending {@code admin}) —
     * called from {@code AdminService.resolveReport} only when a report
     * resolves to a real enforcement action, never for {@code DISMISSED}
     * (Business Rules Module B: "an unsubstantiated report never touches the
     * [Trust] score").
     */
    @Transactional
    public void applyReportsPenalty(UUID userId, BigDecimal penaltyDelta, String reason) {
        TrustScore trust = ensureRow(userId);
        trust.adjustReportsPenalty(penaltyDelta);
        trustScoreRepository.save(trust);
        recalculate(userId, reason, null, null);
    }

    /** {@code GET /admin/analytics?metric=trust_score_distribution} (Phase 9) — count of users per {@link com.gotogether.trust.entity.TrustLevel} band. Keyed by the enum's own name so {@code analytics.service.AnalyticsService} (a different module) never needs this module's entity type directly — same {@code String}-keyed convention as every other cross-module read in this codebase. */
    public Map<String, Long> getScoreDistribution() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (TrustLevel level : TrustLevel.values()) {
            distribution.put(level.name(), trustScoreRepository.countByLevel(level));
        }
        return distribution;
    }

    // --- reads ----------------------------------------------------------------

    /** {@code GET /users/{id}/trust-score} (API Spec Section 12) — public breakdown, no improvement tips (Chapter 2 Section 2.3: "full breakdown is public"). */
    @Transactional
    public TrustScoreResponse getPublicBreakdown(UUID userId) {
        return toResponse(ensureRow(userId), null);
    }

    /** {@code GET /users/me/trust-score} (API Spec Section 4) — self view, includes improvement tips. */
    @Transactional
    public TrustScoreResponse getSelfBreakdown(UUID userId) {
        TrustScore score = ensureRow(userId);
        return toResponse(score, buildImprovementTips(score));
    }

    /** {@code GET /users/me/trust-score/history} (API Spec Section 12) — self only, newest-first. Uses the same {@code OffsetCursor} convention as every other unbounded list in this codebase (see that class's doc). */
    @Transactional
    public CursorPageResponse<TrustScoreHistoryEntry> getHistory(UUID userId, String cursor, int limit) {
        ensureRow(userId);
        int effectiveLimit = limit <= 0 ? DEFAULT_HISTORY_LIMIT : limit;
        int offset = OffsetCursor.decode(cursor);
        var result = trustScoreHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit));
        List<TrustScoreHistoryEntry> items = result.getContent().stream()
                .map(h -> new TrustScoreHistoryEntry(h.getId(), h.getOldScore(), h.getNewScore(), h.getReason(), h.getRelatedReviewId(), h.getRelatedTripId(), h.getCreatedAt()))
                .toList();
        String nextCursor = result.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    // --- internal ---------------------------------------------------------------

    /**
     * Trust Score Recalculation (Trust & Discovery Module A / Chapter 3
     * Section 3.8's flow diagram): read every component fresh, compute the
     * weighted composite, anomaly-check against the prior score, then either
     * apply-and-record or freeze-and-record. A no-op if the score is already
     * frozen — see this class's doc on why nothing here can clear a freeze.
     */
    private void recalculate(UUID userId, String reason, UUID relatedReviewId, UUID relatedTripId) {
        TrustScore trust = ensureRow(userId);
        if (trust.isFrozen()) {
            return;
        }

        BigDecimal reviews = reviewsComponent(userId);
        BigDecimal completion = completionComponent(userId);
        BigDecimal verification = verificationComponent(userId);
        BigDecimal organizer = organizerComponent(userId);
        BigDecimal accountActivity = accountActivityComponent(userId);
        BigDecimal profileCompleteness = profileService.getCompletenessScore(userId);

        BigDecimal weighted = reviews.multiply(WEIGHT_REVIEWS)
                .add(completion.multiply(WEIGHT_COMPLETION))
                .add(verification.multiply(WEIGHT_VERIFICATION))
                .add(organizer.multiply(WEIGHT_ORGANIZER))
                .add(accountActivity.multiply(WEIGHT_ACCOUNT_ACTIVITY))
                .add(profileCompleteness.multiply(WEIGHT_PROFILE_COMPLETENESS))
                .add(trust.getReportsPenalty()); // reports penalty is stored <= 0, so this subtracts
        BigDecimal newScore = clamp(weighted).setScale(1, RoundingMode.HALF_UP);

        BigDecimal oldScore = trust.getCurrentScore();
        if (oldScore.subtract(newScore).abs().compareTo(ANOMALY_THRESHOLD) > 0) {
            trust.freeze();
            trustScoreRepository.save(trust);
            trustScoreHistoryRepository.save(TrustScoreHistory.record(
                    userId, oldScore, oldScore, reason + " (anomaly detected, frozen pending moderator review)", relatedReviewId, relatedTripId));
            return;
        }

        trust.apply(newScore, reviews, completion, verification, organizer, accountActivity, profileCompleteness);
        trustScoreRepository.save(trust);
        if (oldScore.compareTo(newScore) != 0) {
            trustScoreHistoryRepository.save(TrustScoreHistory.record(userId, oldScore, newScore, reason, relatedReviewId, relatedTripId));
        }
    }

    private BigDecimal reviewsComponent(UUID userId) {
        ReviewSubScoreAverages averages = reviewService.getSubScoreAverages(userId);
        if (averages.reviewCount() == 0) {
            return NEUTRAL_NO_DATA;
        }
        // averageSubScore is on a 1-5 scale (the six review sub-scores) -> rescale to 0-10.
        double scaled = (averages.averageSubScore() - 1) / 4.0 * 10.0;
        return BigDecimal.valueOf(scaled).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal completionComponent(UUID userId) {
        MembershipCompletionStats stats = membershipService.getCompletionStats(userId);
        if (stats.totalConcluded() == 0) {
            return NEUTRAL_NO_DATA;
        }
        // Completed and graceful Leaves count as full/near-full quality participation;
        // Removed, a late Leave, and a NO_SHOW-marked attendance all count as zero — a
        // trip you joined but the organizer recorded you as never actually attending
        // gets no completion credit, even though the underlying trip_members row itself
        // reached COMPLETED status (see MembershipCompletionStats' class doc).
        double qualityUnits = stats.completed() + stats.gracefulLeaves() * 0.9;
        double ratio = qualityUnits / stats.totalConcluded();
        return BigDecimal.valueOf(ratio * 10.0).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal verificationComponent(UUID userId) {
        VerificationLevel level = userService.getSummary(userId).verificationLevel();
        int maxOrdinal = VerificationLevel.values().length - 1;
        double scaled = (double) level.ordinal() / maxOrdinal * 10.0;
        return BigDecimal.valueOf(scaled).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal organizerComponent(UUID userId) {
        List<UUID> ownTripIds = tripService.listOwnTrips(userId).stream().map(t -> t.id()).toList();
        OrganizerReliabilityStats stats = joinRequestService.getOrganizerReliabilityStats(ownTripIds);
        if (stats.decidedCount() == 0 && stats.expiredCount() == 0) {
            return NEUTRAL_NO_DATA;
        }
        double base = stats.responseRate() * 10.0;
        if (stats.avgResponseHours() != null) {
            // Slow average responses shave points off, capped so a single very slow response can't zero the component out.
            base -= Math.min(stats.avgResponseHours() / 24.0, 5.0);
        }
        return BigDecimal.valueOf(Math.max(0, base)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal accountActivityComponent(UUID userId) {
        OffsetDateTime createdAt = userService.getAccountCreatedAt(userId);
        double ageDays = Duration.between(createdAt, OffsetDateTime.now()).toHours() / 24.0;
        double scaled = Math.min(ageDays / ACCOUNT_AGE_MAX_DAYS, 1.0) * 10.0;
        return BigDecimal.valueOf(scaled).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.TEN) > 0) return BigDecimal.TEN;
        return value;
    }

    /** {@code ensureRoomAndOrganizerSeat}-style lazy row creation — see this class's doc. */
    private TrustScore ensureRow(UUID userId) {
        return trustScoreRepository.findById(userId).orElseGet(() -> trustScoreRepository.save(TrustScore.seedFor(userId)));
    }

    private List<String> buildImprovementTips(TrustScore score) {
        List<String> tips = new ArrayList<>();
        if (isLow(score.getReviewsComponent())) tips.add("Complete more trips with fellow travellers to earn reviews — they're 40% of your score.");
        if (isLow(score.getCompletionComponent())) tips.add("Finishing trips you join (rather than leaving early) improves your reliability signal.");
        if (isLow(score.getVerificationComponent())) tips.add("Complete Government ID verification to boost your Verification component.");
        if (isLow(score.getProfileCompletenessComponent())) tips.add("Fill out your bio, photo, and travel preferences for a more complete profile.");
        return tips;
    }

    private boolean isLow(BigDecimal component) {
        return component != null && component.compareTo(new BigDecimal("6.0")) < 0;
    }

    private TrustScoreResponse toResponse(TrustScore score, List<String> improvementTips) {
        TrustScoreComponents components = new TrustScoreComponents(
                score.getReviewsComponent(), score.getCompletionComponent(), score.getVerificationComponent(),
                score.getOrganizerComponent(), score.getReportsPenalty(), score.getAccountActivityComponent(),
                score.getProfileCompletenessComponent());
        return new TrustScoreResponse(score.getCurrentScore(), score.getLevel().name(), components, improvementTips);
    }
}
