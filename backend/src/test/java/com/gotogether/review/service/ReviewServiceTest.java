package com.gotogether.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.review.dto.ReviewSubScoreAverages;
import com.gotogether.review.dto.SubmitReviewRequest;
import com.gotogether.review.entity.Review;
import com.gotogether.review.entity.ReviewStatus;
import com.gotogether.review.entity.ReviewVisibility;
import com.gotogether.review.repository.ReviewRepository;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private TripService tripService;
    @Mock private MembershipService membershipService;
    @Mock private ProfileService profileService;

    private ReviewService reviewService;

    private final UUID tripId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID revieweeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, tripService, membershipService, profileService);
        lenient().when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(profileService.getPublicSummary(any())).thenReturn(new ProfilePublicSummary(reviewerId, "Priya", null));
    }

    private TripSummary completedTrip() {
        return new TripSummary(
                tripId, "Manali Trip", TripKind.COMMUNITY, TripStatus.COMPLETED,
                new DestinationSummary(UUID.randomUUID(), "Manali", null, null),
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(5), 1000, 5000, null,
                (short) 6, 4, null, UUID.randomUUID(), "Organizer", null, false, null);
    }

    private SubmitReviewRequest validRequest() {
        return new SubmitReviewRequest(revieweeId, (short) 5, (short) 5, (short) 4, (short) 5, (short) 5, (short) 5, (short) 5, "Great trip!");
    }

    @Test
    void submitRejectsSelfReview() {
        assertThatThrownBy(() -> reviewService.submit(reviewerId, tripId, new SubmitReviewRequest(
                reviewerId, (short) 5, (short) 5, (short) 5, (short) 5, (short) 5, (short) 5, (short) 5, null)))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void submitRejectsWhenTripNotCompleted() {
        when(tripService.getSummary(tripId)).thenReturn(new TripSummary(
                tripId, "Manali Trip", TripKind.COMMUNITY, TripStatus.IN_PROGRESS,
                new DestinationSummary(UUID.randomUUID(), "Manali", null, null),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), 1000, 5000, null,
                (short) 6, 4, null, UUID.randomUUID(), "Organizer", null, false, null));

        assertThatThrownBy(() -> reviewService.submit(reviewerId, tripId, validRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submitRejectsWhenEitherPartyWasNeverAMember() {
        when(tripService.getSummary(tripId)).thenReturn(completedTrip());
        when(membershipService.wasMember(tripId, reviewerId)).thenReturn(true);
        when(membershipService.wasMember(tripId, revieweeId)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.submit(reviewerId, tripId, validRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submitRejectsADuplicateReview() {
        when(tripService.getSummary(tripId)).thenReturn(completedTrip());
        when(membershipService.wasMember(tripId, reviewerId)).thenReturn(true);
        when(membershipService.wasMember(tripId, revieweeId)).thenReturn(true);
        when(reviewRepository.existsByTripIdAndReviewerIdAndRevieweeId(tripId, reviewerId, revieweeId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submit(reviewerId, tripId, validRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submitStaysBlindWhenNoCounterpartYet() {
        when(tripService.getSummary(tripId)).thenReturn(completedTrip());
        when(membershipService.wasMember(tripId, reviewerId)).thenReturn(true);
        when(membershipService.wasMember(tripId, revieweeId)).thenReturn(true);
        when(reviewRepository.existsByTripIdAndReviewerIdAndRevieweeId(tripId, reviewerId, revieweeId)).thenReturn(false);
        when(reviewRepository.findByTripIdAndReviewerIdAndRevieweeId(tripId, revieweeId, reviewerId)).thenReturn(Optional.empty());

        var result = reviewService.submit(reviewerId, tripId, validRequest());

        assertThat(result.review().status()).isEqualTo("SUBMITTED");
        assertThat(result.review().visibility()).isEqualTo("BLIND");
        assertThat(result.justPublishedUserIds()).isEmpty();
    }

    @Test
    void submitPublishesBothSidesWhenCounterpartAlreadySubmitted() {
        when(tripService.getSummary(tripId)).thenReturn(completedTrip());
        when(membershipService.wasMember(tripId, reviewerId)).thenReturn(true);
        when(membershipService.wasMember(tripId, revieweeId)).thenReturn(true);
        when(reviewRepository.existsByTripIdAndReviewerIdAndRevieweeId(tripId, reviewerId, revieweeId)).thenReturn(false);

        Review counterpart = Review.submit(tripId, revieweeId, reviewerId, (short) 4, (short) 4, (short) 4, (short) 4, (short) 4, (short) 4, (short) 4, null);
        when(reviewRepository.findByTripIdAndReviewerIdAndRevieweeId(tripId, revieweeId, reviewerId)).thenReturn(Optional.of(counterpart));

        var result = reviewService.submit(reviewerId, tripId, validRequest());

        assertThat(result.review().status()).isEqualTo("PUBLISHED");
        assertThat(result.review().visibility()).isEqualTo("PUBLISHED");
        assertThat(counterpart.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(result.justPublishedUserIds()).containsExactlyInAnyOrder(reviewerId, revieweeId);
    }

    @Test
    void subScoreAveragesReturnsNoneWhenNoPublishedReviews() {
        when(reviewRepository.findByRevieweeIdAndStatusAndVisibility(revieweeId, ReviewStatus.SUBMITTED, ReviewVisibility.BLIND)).thenReturn(List.of());
        when(reviewRepository.findByRevieweeIdAndStatus(revieweeId, ReviewStatus.PUBLISHED)).thenReturn(List.of());

        ReviewSubScoreAverages averages = reviewService.getSubScoreAverages(revieweeId);

        assertThat(averages.reviewCount()).isZero();
        assertThat(averages).isEqualTo(ReviewSubScoreAverages.NONE);
    }

    @Test
    void subScoreAveragesComputesTheMeanAcrossPublishedReviews() {
        Review r1 = Review.submit(tripId, reviewerId, revieweeId, (short) 5, (short) 5, (short) 5, (short) 5, (short) 5, (short) 5, (short) 5, null);
        Review r2 = Review.submit(tripId, UUID.randomUUID(), revieweeId, (short) 3, (short) 3, (short) 3, (short) 3, (short) 3, (short) 3, (short) 3, null);
        when(reviewRepository.findByRevieweeIdAndStatusAndVisibility(revieweeId, ReviewStatus.SUBMITTED, ReviewVisibility.BLIND)).thenReturn(List.of());
        when(reviewRepository.findByRevieweeIdAndStatus(revieweeId, ReviewStatus.PUBLISHED)).thenReturn(List.of(r1, r2));

        ReviewSubScoreAverages averages = reviewService.getSubScoreAverages(revieweeId);

        assertThat(averages.reviewCount()).isEqualTo(2);
        assertThat(averages.averageSubScore()).isEqualTo(4.0);
    }
}
