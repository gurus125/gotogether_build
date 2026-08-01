package com.gotogether.review.controller;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.notification.service.NotificationService;
import com.gotogether.review.dto.ReviewResponse;
import com.gotogether.review.dto.SubmitReviewRequest;
import com.gotogether.review.service.ReviewService;
import com.gotogether.trust.service.TrustService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review APIs (API Specification Section 11) — {@code POST /reviews/{id}/report}
 * intentionally absent, see {@code ReviewService}'s class doc.
 */
@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final TrustService trustService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;

    public ReviewController(
            ReviewService reviewService, TrustService trustService, NotificationService notificationService,
            AnalyticsService analyticsService) {
        this.reviewService = reviewService;
        this.trustService = trustService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
    }

    /**
     * A Published review is one of Trust Score's recalculation triggers
     * (Chapter 3 Section 3.8, Trust & Discovery Module A) — wired here rather
     * than inside {@code ReviewService} itself, for the exact cycle-avoidance
     * reason {@code ReviewService}'s class doc explains ({@code trust}
     * already depends on {@code review}). Also notifies each just-published
     * user their Trust Score changed ({@code trust_update}, Phase 6) —
     * {@code notification} has no outbound dependencies, so no cycle risk.
     */
    @PostMapping("/trips/{id}/reviews")
    public ResponseEntity<ReviewResponse> submit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SubmitReviewRequest request) {
        var result = reviewService.submit(principal.userId(), id, request);
        analyticsService.record(
                "review_submitted", principal.userId(), ReferencedEntityType.REVIEWS.tableName(), result.review().id(), null);
        result.justPublishedUserIds().forEach(userId -> {
            trustService.recalculateForReviewPublished(userId);
            analyticsService.record(
                    "trust_score_updated", userId, ReferencedEntityType.REVIEWS.tableName(), result.review().id(), null);
            notificationService.create(
                    userId, null, "TRUST_UPDATE", ReferencedEntityType.REVIEWS.tableName(), result.review().id(),
                    "New review received", "A new review just updated your Trust Score.", "low");
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(result.review());
    }

    @GetMapping("/users/{id}/reviews")
    public CursorPageResponse<ReviewResponse> published(
            @PathVariable UUID id, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return reviewService.getPublishedReviews(id, cursor, limit);
    }
}
