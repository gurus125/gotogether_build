package com.gotogether.review.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.review.dto.ReviewResponse;
import com.gotogether.review.dto.ReviewSubScoreAverages;
import com.gotogether.review.dto.SubmitReviewRequest;
import com.gotogether.review.entity.Review;
import com.gotogether.review.entity.ReviewStatus;
import com.gotogether.review.entity.ReviewVisibility;
import com.gotogether.review.repository.ReviewRepository;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The review module's only entry point for other modules — everything else
 * ({@code reviews} entity/repository) is package-private to this module in
 * practice (enforced by {@code ArchitectureTest}).
 *
 * <p>Depends one-directionally on {@code trip} (trip status/title), {@code
 * membership} (Review eligibility — did the reviewer/reviewee ever hold a
 * {@code trip_members} row on this trip, Chapter 3 Section 3.7/3.13), and
 * {@code profile} (reviewer display name/photo) — never the reverse. {@code
 * trust} depends on <em>this</em> module (reading Published review averages
 * via {@link #getSubScoreAverages}), which is exactly why the "a Published
 * review triggers Trust Score recalculation" rule (Chapter 3 Section 3.8,
 * Trust & Discovery Module A) is wired at the <b>controller layer</b> ({@code
 * ReviewController}, after {@link #submit} returns) instead of from inside
 * this service — {@code review -> trust} would create a cycle since {@code
 * trust -> review} already exists. Same composition-at-the-controller pattern
 * already established for {@code trip}/{@code chat} (see {@code
 * ChatService}'s class doc) and {@code trip}/{@code membership} (see {@code
 * TripSummary#withJoinedCount}'s doc).
 *
 * <p><b>Scoped down for this pass</b> (flagged here rather than silently
 * dropped): {@code POST /reviews/{id}/report} (API Spec Section 11) is not
 * implemented — it needs a {@code reports} table row, which belongs to the
 * not-yet-built {@code report} module (Phase 6+), same rationale as {@code
 * chat}'s deferred moderation endpoints. There is also no "list who I can
 * still review on this trip" endpoint — the API Specification's Review APIs
 * table (Section 11) only names three endpoints (submit, list published,
 * report), and Section 19 states that table is "the complete contract," so
 * inventing a fourth would be exactly the kind of undocumented API change
 * the project's process rule forbids. The Flutter client is expected to
 * build its own-review-status view from {@code GET /trips/{id}/members}
 * (already returns the roster) plus this module's 409 {@code DUPLICATE_REVIEW}
 * response to detect already-reviewed pairs.
 */
@Service
public class ReviewService {

    /** Chapter 3 Section 3.7 / Trust & Discovery Module B's recommended default: 14 days from the reviewer's own trip completion. */
    private static final int REVIEW_WINDOW_DAYS = 14;

    /** A sub-score of 4 or 5 counts toward that dimension's "highlighted trait" tally (Trust & Discovery Module B: "recurring positive traits... surfaced when they appear across 3+ reviews"). */
    private static final int TRAIT_THRESHOLD_RATING = 4;
    private static final int TRAIT_MIN_OCCURRENCES = 3;
    private static final int DEFAULT_LIMIT = 20;

    private final ReviewRepository reviewRepository;
    private final TripService tripService;
    private final MembershipService membershipService;
    private final ProfileService profileService;

    public ReviewService(
            ReviewRepository reviewRepository, TripService tripService, MembershipService membershipService,
            ProfileService profileService) {
        this.reviewRepository = reviewRepository;
        this.tripService = tripService;
        this.membershipService = membershipService;
        this.profileService = profileService;
    }

    /**
     * {@code POST /trips/{id}/reviews} (API Spec Section 11). Returns which
     * user ids (if any) just crossed into Published, so the controller can
     * drive Trust Score recalculation for exactly those users without this
     * service depending on {@code trust} itself (see this class's doc).
     */
    @Transactional
    public SubmitResult submit(UUID reviewerId, UUID tripId, SubmitReviewRequest request) {
        if (request.revieweeId().equals(reviewerId)) {
            throw new UnprocessableEntityException("You cannot review yourself.");
        }
        TripSummary trip = tripService.getSummary(tripId);
        if (trip.status() != TripStatus.COMPLETED) {
            throw new ConflictException("Reviews can only be submitted once this trip is Completed.");
        }
        if (!membershipService.wasMember(tripId, reviewerId) || !membershipService.wasMember(tripId, request.revieweeId())) {
            throw new ConflictException("Both you and this person must have been members of this trip to review each other.");
        }
        if (reviewRepository.existsByTripIdAndReviewerIdAndRevieweeId(tripId, reviewerId, request.revieweeId())) {
            throw new ConflictException("You've already reviewed this person for this trip.");
        }

        Review review = Review.submit(
                tripId, reviewerId, request.revieweeId(), request.ratingBehaviour(), request.ratingPunctuality(),
                request.ratingCommunication(), request.ratingCooperation(), request.ratingSafety(),
                request.ratingReliability(), request.overallRating(), request.comment());
        final Review savedReview = reviewRepository.save(review);

        List<UUID> justPublished = new ArrayList<>();
        // Double-Blind Review flow: the counterpart direction (reviewee reviewing reviewer, same trip).
        reviewRepository.findByTripIdAndReviewerIdAndRevieweeId(tripId, request.revieweeId(), reviewerId)
                .filter(Review::isBlindAndSubmitted)
                .ifPresent(counterpart -> {
                    counterpart.publish();
                    reviewRepository.save(counterpart);
                    savedReview.publish();
                    justPublished.add(reviewerId);
                    justPublished.add(request.revieweeId());
                });
        Review result = reviewRepository.save(savedReview);

        return new SubmitResult(toResponse(result, false), justPublished);
    }

    /** {@code GET /users/{id}/reviews} (API Spec Section 11) — Published only, most-recent-first, with recurring-trait tags highlighted. Also runs the lazy window-close sweep (see this class's doc) for the viewed user before reading. */
    @Transactional
    public CursorPageResponse<ReviewResponse> getPublishedReviews(UUID revieweeId, String cursor, int limit) {
        applyLazyWindowClose(revieweeId);
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        int offset = OffsetCursor.decode(cursor);
        var page = reviewRepository.findByRevieweeIdAndStatusOrderByPublishedAtDesc(
                revieweeId, ReviewStatus.PUBLISHED, PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit));

        List<Review> published = reviewRepository.findByRevieweeIdAndStatus(revieweeId, ReviewStatus.PUBLISHED);
        List<String> highlightedTraits = computeHighlightedTraits(published);

        List<ReviewResponse> items = page.getContent().stream().map(r -> toResponse(r, true, highlightedTraits)).toList();
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    /** Rolling average of every Published review's six sub-scores for a user — the {@code trust} module's Reviews-component input (40% weight). Also runs the lazy window-close sweep first, so a review whose 14-day window just lapsed counts immediately rather than waiting for someone to open the reviews list. */
    @Transactional
    public ReviewSubScoreAverages getSubScoreAverages(UUID userId) {
        applyLazyWindowClose(userId);
        List<Review> published = reviewRepository.findByRevieweeIdAndStatus(userId, ReviewStatus.PUBLISHED);
        if (published.isEmpty()) {
            return ReviewSubScoreAverages.NONE;
        }
        double sum = published.stream().mapToDouble(Review::averageSubScore).sum();
        return new ReviewSubScoreAverages(published.size(), sum / published.size());
    }

    /**
     * Phase 7's {@code GET /companies/{id}}'s {@code aggregate_rating} —
     * "average of per-trip overall ratings, not the six individual
     * sub-scores used for peer Trust Score, a business isn't rated on
     * 'Punctuality' as a personal trait" (Operations Module A). {@code
     * Optional.empty()} until at least one Published review exists against
     * any of the given trips — never fabricated as {@code 0.0}.
     */
    public Optional<Double> averageOverallRatingForTrips(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Optional.empty();
        }
        List<Review> published = reviewRepository.findByTripIdInAndStatus(tripIds, ReviewStatus.PUBLISHED);
        if (published.isEmpty()) {
            return Optional.empty();
        }
        double sum = published.stream().mapToInt(Review::getOverallRating).sum();
        return Optional.of(sum / published.size());
    }

    // --- Phase 8 admin entry points (called by admin.service.AdminService — see this class's doc for the composition-lives-in-admin convention) ---

    /**
     * {@code POST /admin/reports/{id}/resolve} with {@code
     * resolution_action=content_removed} against a {@code review}-typed
     * report — hides the review from every public view without deleting the
     * row (Operations Module C's "hide" tier). Returns the reviewer id so
     * {@code AdminService} can also apply any account-level ladder action
     * against the person who wrote it, without this module reaching into
     * {@code user}/{@code trust} itself.
     */
    @Transactional
    public UUID adminHide(UUID reviewId, String notes) {
        Review review = getReviewOrThrow(reviewId);
        review.hide(notes);
        reviewRepository.save(review);
        return review.getReviewerId();
    }

    /** Terminal counterpart to {@link #adminHide} (Operations Module C's "remove" tier). */
    @Transactional
    public UUID adminRemove(UUID reviewId, String notes) {
        Review review = getReviewOrThrow(reviewId);
        review.remove(notes);
        reviewRepository.save(review);
        return review.getReviewerId();
    }

    /** Who wrote this review — used by {@code AdminService} to resolve a {@code review}-typed report's account-level enforcement target without a mutation. */
    public UUID getReviewerId(UUID reviewId) {
        return getReviewOrThrow(reviewId).getReviewerId();
    }

    // --- internal helpers ---------------------------------------------------

    private Review getReviewOrThrow(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
    }

    /**
     * No scheduled job exists yet to publish a review whose 14-day window
     * closed with only one side submitted (Chapter 3 Section 3.7: "that
     * single review still Publishes at window-close") — same "lazy sweep on
     * read, real cron job is a later-phase concern" convention already used
     * by {@code JoinRequestService#applyLazyExpiry}. The window is anchored
     * to the <em>reviewer's own</em> {@code trip_members.completedAt} (when
     * their personal review opportunity opened), not the review row's {@code
     * createdAt} — a reviewer who submits on day 13 still only has 1 day left
     * for their counterpart, matching Chapter 3 3.7's per-pair, not
     * per-submission, window.
     */
    private void applyLazyWindowClose(UUID revieweeId) {
        List<Review> blind = reviewRepository.findByRevieweeIdAndStatusAndVisibility(revieweeId, ReviewStatus.SUBMITTED, ReviewVisibility.BLIND);
        for (Review review : blind) {
            OffsetDateTime windowOpenedAt = membershipService.getCompletedAt(review.getTripId(), review.getReviewerId())
                    .orElse(review.getCreatedAt());
            if (windowOpenedAt.plusDays(REVIEW_WINDOW_DAYS).isBefore(OffsetDateTime.now())) {
                review.publish();
                reviewRepository.save(review);
            }
        }
    }

    private List<String> computeHighlightedTraits(List<Review> published) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Respectful", published.stream().filter(r -> r.getRatingBehaviour() >= TRAIT_THRESHOLD_RATING).count());
        counts.put("Punctual", published.stream().filter(r -> r.getRatingPunctuality() >= TRAIT_THRESHOLD_RATING).count());
        counts.put("Great communicator", published.stream().filter(r -> r.getRatingCommunication() >= TRAIT_THRESHOLD_RATING).count());
        counts.put("Team player", published.stream().filter(r -> r.getRatingCooperation() >= TRAIT_THRESHOLD_RATING).count());
        counts.put("Safety-conscious", published.stream().filter(r -> r.getRatingSafety() >= TRAIT_THRESHOLD_RATING).count());
        counts.put("Reliable", published.stream().filter(r -> r.getRatingReliability() >= TRAIT_THRESHOLD_RATING).count());
        return counts.entrySet().stream().filter(e -> e.getValue() >= TRAIT_MIN_OCCURRENCES).map(Map.Entry::getKey).toList();
    }

    private ReviewResponse toResponse(Review r, boolean includeReviewerInfo) {
        return toResponse(r, includeReviewerInfo, List.of());
    }

    private ReviewResponse toResponse(Review r, boolean includeReviewerInfo, List<String> highlightedTraits) {
        String reviewerName = null;
        String reviewerPhoto = null;
        if (includeReviewerInfo) {
            ProfilePublicSummary profile = profileService.getPublicSummary(r.getReviewerId());
            reviewerName = profile.displayName();
            reviewerPhoto = profile.photoUrl();
        }
        return new ReviewResponse(
                r.getId(), r.getTripId(), r.getReviewerId(), reviewerName, reviewerPhoto, r.getRevieweeId(),
                r.getRatingBehaviour(), r.getRatingPunctuality(), r.getRatingCommunication(), r.getRatingCooperation(),
                r.getRatingSafety(), r.getRatingReliability(), r.getOverallRating(), r.getComment(),
                r.getStatus().name(), r.getVisibility().name(), r.getPublishedAt(), r.getCreatedAt(), highlightedTraits);
    }

    /** {@link #submit}'s result — {@code justPublishedUserIds} is empty unless this submission was the second side of a pair, or the pair happened to complete via the lazy sweep. */
    public record SubmitResult(ReviewResponse review, List<UUID> justPublishedUserIds) {}
}
