package com.gotogether.review.repository;

import com.gotogether.review.entity.Review;
import com.gotogether.review.entity.ReviewStatus;
import com.gotogether.review.entity.ReviewVisibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByTripIdAndReviewerIdAndRevieweeId(UUID tripId, UUID reviewerId, UUID revieweeId);

    /** The counterpart direction of a just-submitted review — swapped reviewer/reviewee (Double-Blind Review flow). */
    Optional<Review> findByTripIdAndReviewerIdAndRevieweeId(UUID tripId, UUID reviewerId, UUID revieweeId);

    Page<Review> findByRevieweeIdAndStatusOrderByPublishedAtDesc(UUID revieweeId, ReviewStatus status, Pageable pageable);

    /** Every published review naming this user, for Trust Score's rolling review-based sub-score average (no pagination — averaged in full). */
    List<Review> findByRevieweeIdAndStatus(UUID revieweeId, ReviewStatus status);

    /** Lazy window-close sweep candidates (mirrors {@code JoinRequestService#applyLazyExpiry}'s no-scheduled-job convention) — every Blind-and-Submitted review naming this user, checked for window-close on read. */
    List<Review> findByRevieweeIdAndStatusAndVisibility(UUID revieweeId, ReviewStatus status, ReviewVisibility visibility);

    /** Every Published review against any of a set of trips — feeds the Company Profile's aggregate rating (Phase 7), grouped by {@code trip_id} rather than {@code reviewee_id} since a Verified Partner Trip's organizer may not be the same staff user across trips. */
    List<Review> findByTripIdInAndStatus(List<UUID> tripIds, ReviewStatus status);
}
