package com.gotogether.trust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.joinrequest.dto.OrganizerReliabilityStats;
import com.gotogether.joinrequest.service.JoinRequestService;
import com.gotogether.membership.dto.MembershipCompletionStats;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.review.dto.ReviewSubScoreAverages;
import com.gotogether.review.service.ReviewService;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.entity.TrustScore;
import com.gotogether.trust.entity.TrustScoreHistory;
import com.gotogether.trust.repository.TrustScoreHistoryRepository;
import com.gotogether.trust.repository.TrustScoreRepository;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrustServiceTest {

    @Mock private TrustScoreRepository trustScoreRepository;
    @Mock private TrustScoreHistoryRepository trustScoreHistoryRepository;
    @Mock private ReviewService reviewService;
    @Mock private MembershipService membershipService;
    @Mock private UserService userService;
    @Mock private TripService tripService;
    @Mock private JoinRequestService joinRequestService;
    @Mock private ProfileService profileService;

    private TrustService trustService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        trustService = new TrustService(
                trustScoreRepository, trustScoreHistoryRepository, reviewService, membershipService, userService,
                tripService, joinRequestService, profileService);
        lenient().when(trustScoreRepository.save(any(TrustScore.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(trustScoreHistoryRepository.save(any(TrustScoreHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(trustScoreRepository.findById(userId)).thenReturn(Optional.empty());
    }

    private TripSummary ownedTrip() {
        return new TripSummary(
                UUID.randomUUID(), "Goa Trip", TripKind.COMMUNITY, TripStatus.COMPLETED,
                new DestinationSummary(UUID.randomUUID(), "Goa", null, null), LocalDate.now().minusDays(20),
                LocalDate.now().minusDays(15), 1000, 5000, null, (short) 6, 5, null, userId, "Me", null, true, null);
    }

    private void stubModerateInputs() {
        when(reviewService.getSubScoreAverages(userId)).thenReturn(new ReviewSubScoreAverages(3, 4.0));
        when(membershipService.getCompletionStats(userId)).thenReturn(new MembershipCompletionStats(8, 0, 0, 2, 0));
        when(userService.getSummary(userId)).thenReturn(new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.VERIFIED, VerificationLevel.EMAIL));
        when(tripService.listOwnTrips(userId)).thenReturn(List.of(ownedTrip()));
        when(joinRequestService.getOrganizerReliabilityStats(any())).thenReturn(new OrganizerReliabilityStats(4, 1, 2.0));
        when(userService.getAccountCreatedAt(userId)).thenReturn(OffsetDateTime.now().minusDays(365));
        when(profileService.getCompletenessScore(userId)).thenReturn(new BigDecimal("8.0"));
    }

    @Test
    void seedsAFreshRowAt6point5ForABrandNewUser() {
        when(reviewService.getSubScoreAverages(any())).thenReturn(ReviewSubScoreAverages.NONE);
        when(membershipService.getCompletionStats(any())).thenReturn(new MembershipCompletionStats(0, 0, 0, 0, 0));
        when(userService.getSummary(any())).thenReturn(new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.NONE));
        when(tripService.listOwnTrips(any())).thenReturn(List.of());
        when(joinRequestService.getOrganizerReliabilityStats(any())).thenReturn(new OrganizerReliabilityStats(0, 0, null));
        when(userService.getAccountCreatedAt(any())).thenReturn(OffsetDateTime.now());
        when(profileService.getCompletenessScore(any())).thenReturn(new BigDecimal("0.0"));

        var response = trustService.getPublicBreakdown(userId);

        assertThat(response.currentScore()).isEqualByComparingTo("6.5");
        assertThat(response.level()).isEqualTo("BUILDING");
        assertThat(response.improvementTips()).isNull();
    }

    @Test
    void recalculationAppliesAWeightedCompositeWithinNormalRange() {
        stubModerateInputs();

        trustService.recalculateForReviewPublished(userId);
        var response = trustService.getSelfBreakdown(userId);

        // Weighted mix of moderate-but-not-extreme component values should land comfortably
        // between the seeded 6.5 and a perfect 10 without tripping the anomaly threshold.
        assertThat(response.currentScore().doubleValue()).isBetween(6.0, 9.0);
        assertThat(response.components().reviews()).isNotNull();
        assertThat(response.components().completion()).isNotNull();
        assertThat(response.components().verification()).isNotNull();
        verify(trustScoreHistoryRepository, times(1)).save(any(TrustScoreHistory.class));
    }

    @Test
    void anomalousJumpFreezesTheScoreInsteadOfApplyingIt() {
        // Maxing out every component from a 6.5 seed is a >2.5-point swing (Module A's own anomaly example).
        when(reviewService.getSubScoreAverages(userId)).thenReturn(new ReviewSubScoreAverages(5, 5.0));
        when(membershipService.getCompletionStats(userId)).thenReturn(new MembershipCompletionStats(10, 0, 0, 0, 0));
        when(userService.getSummary(userId)).thenReturn(new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.VERIFIED, VerificationLevel.ID_APPROVED));
        when(tripService.listOwnTrips(userId)).thenReturn(List.of(ownedTrip()));
        when(joinRequestService.getOrganizerReliabilityStats(any())).thenReturn(new OrganizerReliabilityStats(5, 0, 0.0));
        when(userService.getAccountCreatedAt(userId)).thenReturn(OffsetDateTime.now().minusDays(730));
        when(profileService.getCompletenessScore(userId)).thenReturn(new BigDecimal("10.0"));

        trustService.recalculateForReviewPublished(userId);
        var response = trustService.getPublicBreakdown(userId);

        // Frozen: the seeded 6.5 must be untouched, not the computed ~10.
        assertThat(response.currentScore()).isEqualByComparingTo("6.5");
    }

    @Test
    void aFrozenScoreIsNotRecalculatedOnANewTrigger() {
        TrustScore frozen = TrustScore.seedFor(userId);
        frozen.freeze();
        when(trustScoreRepository.findById(userId)).thenReturn(Optional.of(frozen));

        assertThatCode(() -> trustService.recalculateForTripCompleted(UUID.randomUUID(), userId)).doesNotThrowAnyException();

        verify(reviewService, never()).getSubScoreAverages(any());
        verify(trustScoreHistoryRepository, never()).save(any());
    }
}
