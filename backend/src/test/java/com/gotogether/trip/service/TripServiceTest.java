package com.gotogether.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.company.service.CompanyService;
import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.destination.entity.DestinationCategory;
import com.gotogether.destination.service.DestinationService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.CancelTripRequest;
import com.gotogether.trip.dto.CreateTripRequest;
import com.gotogether.trip.dto.UpdateTripRequest;
import com.gotogether.trip.entity.Trip;
import com.gotogether.trip.entity.TripImage;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.repository.SavedTripRepository;
import com.gotogether.trip.repository.TripImageRepository;
import com.gotogether.trip.repository.TripRepository;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
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
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private TripImageRepository tripImageRepository;
    @Mock private SavedTripRepository savedTripRepository;
    @Mock private UserService userService;
    @Mock private ProfileService profileService;
    @Mock private DestinationService destinationService;
    @Mock private CompanyService companyService;

    private TripService tripService;

    private final UUID organizerId = UUID.randomUUID();
    private final UUID destinationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tripService = new TripService(
                tripRepository, tripImageRepository, savedTripRepository, userService, profileService, destinationService,
                companyService);
        // @Value is only processed inside a real Spring context — a plain
        // `new TripService(...)` otherwise leaves this at Java's boolean
        // default (false), which silently no-ops requireIdApproved and would
        // make createDraftThrowsWhenOrganizerIsNotIdApproved below pass
        // vacuously (no exception thrown, because the check never runs).
        // Found while adding the equivalent CompanyServiceTest coverage.
        org.springframework.test.util.ReflectionTestUtils.setField(tripService, "enforceIdApproval", true);
        lenient().when(tripImageRepository.findByTripIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());
        lenient().when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(destinationService.getSummary(destinationId))
                .thenReturn(new DestinationSummary(destinationId, "Manali", DestinationCategory.MOUNTAINS, null));
    }

    private CreateTripRequest validRequest() {
        LocalDate start = LocalDate.now().plusDays(10);
        return new CreateTripRequest(destinationId, start, start.plusDays(4), false, 5000, 10000, "Manali Backpacking Trip", "A relaxed trip.", null, null);
    }

    @Test
    void createDraftThrowsWhenOrganizerIsNotIdApproved() {
        when(userService.getSummary(organizerId)).thenReturn(
                new UserSummary(organizerId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.EMAIL));

        assertThatThrownBy(() -> tripService.createDraft(organizerId, validRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createDraftThrowsWhenStartDateIsNotAtLeastTomorrow() {
        when(userService.getSummary(organizerId)).thenReturn(
                new UserSummary(organizerId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
        LocalDate today = LocalDate.now();
        CreateTripRequest request = new CreateTripRequest(destinationId, today, today.plusDays(3), false, null, null, "Trip Title Here", null, null, null);

        assertThatThrownBy(() -> tripService.createDraft(organizerId, request))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void createDraftThrowsWhenEndDateIsBeforeStartDate() {
        when(userService.getSummary(organizerId)).thenReturn(
                new UserSummary(organizerId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
        LocalDate start = LocalDate.now().plusDays(5);
        CreateTripRequest request = new CreateTripRequest(destinationId, start, start.minusDays(1), false, null, null, "Trip Title Here", null, null, null);

        assertThatThrownBy(() -> tripService.createDraft(organizerId, request))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void createDraftThrowsWhenBudgetMaxIsBelowBudgetMin() {
        when(userService.getSummary(organizerId)).thenReturn(
                new UserSummary(organizerId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
        LocalDate start = LocalDate.now().plusDays(5);
        CreateTripRequest request = new CreateTripRequest(destinationId, start, start.plusDays(2), false, 10000, 5000, "Trip Title Here", null, null, null);

        assertThatThrownBy(() -> tripService.createDraft(organizerId, request))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void createDraftSucceedsForAnIdApprovedOrganizer() {
        when(userService.getSummary(organizerId)).thenReturn(
                new UserSummary(organizerId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));

        var response = tripService.createDraft(organizerId, validRequest());

        assertThat(response.organizerId()).isEqualTo(organizerId);
        assertThat(response.status().name()).isEqualTo("DRAFT");
        assertThat(response.kind().name()).isEqualTo("COMMUNITY");
    }

    @Test
    void publishThrowsWhenTheTripIsNotADraft() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.publish(organizerId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void publishThrowsWhenDescriptionIsMissing() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", null, false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.publish(organizerId, UUID.randomUUID()))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void publishThrowsWhenCallerIsNotTheOrganizer() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.publish(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void publishSetsStatusToPublishedAndStampsPublishedAt() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "A full description.", false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        var response = tripService.publish(organizerId, UUID.randomUUID());

        assertThat(response.status().name()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void cancelThrowsWhenCallerIsNeitherOrganizerNorModerator() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancel(UUID.randomUUID(), AccountRole.INDIVIDUAL, UUID.randomUUID(), new CancelTripRequest("changed my mind")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancelSucceedsForAModeratorEvenWithoutOwnership() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        var response = tripService.cancel(UUID.randomUUID(), AccountRole.MODERATOR, UUID.randomUUID(), new CancelTripRequest("policy violation"));

        assertThat(response.status().name()).isEqualTo("CANCELLED");
        assertThat(response.cancellationReason()).isEqualTo("policy violation");
    }

    @Test
    void cancelThrowsWhenTheTripIsAlreadyTerminal() {
        Trip trip = publishedTrip();
        trip.cancel("first cancellation");
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancel(organizerId, AccountRole.INDIVIDUAL, UUID.randomUUID(), new CancelTripRequest("again")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteTripThrowsWhenTheTripIsNotADraft() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.deleteTrip(organizerId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteTripSucceedsForAnOwnedDraft() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        tripService.deleteTrip(organizerId, UUID.randomUUID());
        // No exception thrown is the pass condition here — verifying the repository interaction:
    }

    @Test
    void getTripDetailsThrowsWhenADraftIsViewedByANonOrganizer() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTripDetails(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTripDetailsSucceedsWhenTheDraftIsViewedByItsOwnOrganizer() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        when(userService.getSummary(organizerId)).thenReturn(
                new UserSummary(organizerId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
        when(profileService.getPublicSummary(organizerId)).thenReturn(new ProfilePublicSummary(organizerId, "Maya R.", null));

        var response = tripService.getTripDetails(organizerId, UUID.randomUUID());

        assertThat(response.organizer().displayName()).isEqualTo("Maya R.");
        assertThat(response.membersPreview()).isEmpty();
        assertThat(response.compatibilityScore()).isNull();
    }

    @Test
    void saveTripThrowsWhenAlreadySaved() {
        UUID tripId = UUID.randomUUID();
        Trip trip = publishedTrip();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(savedTripRepository.existsByUserIdAndTripId(organizerId, tripId)).thenReturn(true);

        assertThatThrownBy(() -> tripService.saveTrip(organizerId, tripId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void assertOrganizerThrowsWhenCallerIsNotTheOrganizer() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.assertOrganizer(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertOrganizerSucceedsForTheRealOrganizer() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        // No exception is the pass condition.
        tripService.assertOrganizer(organizerId, UUID.randomUUID());
    }

    @Test
    void addImageThrowsWhenCallerIsNotTheOrganizer() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.addImage(UUID.randomUUID(), UUID.randomUUID(), "https://example.com/a.jpg", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addImageAppendsAtNextDisplayOrder() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        when(tripImageRepository.findByTripIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(mockImage(true)));
        when(tripImageRepository.save(any(TripImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tripService.addImage(organizerId, UUID.randomUUID(), "https://example.com/b.jpg", false);

        assertThat(response.displayOrder()).isEqualTo((short) 1);
        assertThat(response.imageUrl()).isEqualTo("https://example.com/b.jpg");
        assertThat(response.primary()).isFalse();
    }

    @Test
    void addImageAsPrimaryUnsetsThePreviousPrimaryImage() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        TripImage previousPrimary = mockImage(true);
        when(tripImageRepository.findByTripIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(previousPrimary));
        when(tripImageRepository.save(any(TripImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tripService.addImage(organizerId, UUID.randomUUID(), "https://example.com/c.jpg", true);

        assertThat(previousPrimary.isPrimary()).isFalse();
        assertThat(response.primary()).isTrue();
    }

    @Test
    void deleteImageThrowsWhenCallerIsNotTheOrganizer() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.deleteImage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteImageThrowsWhenTheImageBelongsToADifferentTrip() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        TripImage image = mockImage(false);
        when(tripImageRepository.findById(any())).thenReturn(Optional.of(image));

        // trip.getId() is null in this unit test (never persisted via Hibernate),
        // and mockImage() stubs a random, different tripId — exercising the
        // "does this image actually belong to this trip" guard in deleteImage.
        assertThatThrownBy(() -> tripService.deleteImage(organizerId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- system-triggered transitions (TripLifecycleScheduler) ---------------

    @Test
    void systemMarkInProgressThrowsForATripThatIsNotEligible() {
        // Still Draft — never published, so not in STATUSES_ELIGIBLE_FOR_IN_PROGRESS.
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(4), null, null);
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.systemMarkInProgress(UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void systemMarkInProgressSucceedsForAPublishedTrip() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(4), null, null);
        trip.publish();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        var response = tripService.systemMarkInProgress(UUID.randomUUID());

        assertThat(response.status().name()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void systemMarkCompletedThrowsWhenTheTripIsNotInProgress() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(4), null, null);
        trip.publish();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.systemMarkCompleted(UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void systemMarkCompletedSucceedsForAnInProgressTrip() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "desc", false,
                LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), null, null);
        trip.publish();
        trip.startInProgress();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));

        var response = tripService.systemMarkCompleted(UUID.randomUUID());

        assertThat(response.status().name()).isEqualTo("COMPLETED");
    }

    @Test
    void findTripIdsReadyToStartDelegatesToTheRepositoryQuery() {
        Trip trip = publishedTrip();
        org.springframework.test.util.ReflectionTestUtils.setField(trip, "id", organizerId);
        when(tripRepository.findByStatusInAndStartDateLessThanEqual(any(), any())).thenReturn(List.of(trip));

        var ids = tripService.findTripIdsReadyToStart();

        assertThat(ids).containsExactly(organizerId);
    }

    @Test
    void findTripIdsReadyToCompleteDelegatesToTheRepositoryQuery() {
        Trip trip = publishedTrip();
        org.springframework.test.util.ReflectionTestUtils.setField(trip, "id", organizerId);
        when(tripRepository.findByStatusAndEndDateLessThan(eq(TripStatus.IN_PROGRESS), any())).thenReturn(List.of(trip));

        var ids = tripService.findTripIdsReadyToComplete();

        assertThat(ids).containsExactly(organizerId);
    }

    // --- Manage Trip (PATCH /trips/{id}: group size / meeting point / approval settings) ---

    @Test
    void updateTripAppliesGroupSizeMeetingPointAndApprovalFields() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        var request = new UpdateTripRequest(
                null, null, null, null, null, null, null, null,
                4, 8, "Cafe Coffee Day, Connaught Place", false, false);

        var response = tripService.updateTrip(organizerId, UUID.randomUUID(), request, 0L);

        assertThat(response.minGroupSize()).isEqualTo((short) 4);
        assertThat(response.maxGroupSize()).isEqualTo((short) 8);
        assertThat(response.meetingPoint()).isEqualTo("Cafe Coffee Day, Connaught Place");
        assertThat(response.isApprovalRequired()).isFalse();
        assertThat(response.isWaitlistAllowed()).isFalse();
    }

    @Test
    void updateTripThrowsWhenMaxGroupSizeWouldDropBelowCurrentActiveMembers() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        var request = new UpdateTripRequest(null, null, null, null, null, null, null, null, null, 2, null, null, null);

        assertThatThrownBy(() -> tripService.updateTrip(organizerId, UUID.randomUUID(), request, 3L))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void updateTripAllowsMaxGroupSizeEqualToCurrentActiveMembers() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        var request = new UpdateTripRequest(null, null, null, null, null, null, null, null, null, 3, null, null, null);

        var response = tripService.updateTrip(organizerId, UUID.randomUUID(), request, 3L);

        assertThat(response.maxGroupSize()).isEqualTo((short) 3);
    }

    @Test
    void updateTripThrowsWhenMaxGroupSizeIsBelowMinGroupSize() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        var request = new UpdateTripRequest(null, null, null, null, null, null, null, null, 10, 5, null, null, null);

        assertThatThrownBy(() -> tripService.updateTrip(organizerId, UUID.randomUUID(), request, 0L))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void updateTripThrowsWhenTripIsInProgress() {
        Trip trip = publishedTrip();
        trip.startInProgress();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        var request = new UpdateTripRequest(null, null, null, null, null, null, null, null, null, null, "New meeting point", null, null);

        assertThatThrownBy(() -> tripService.updateTrip(organizerId, UUID.randomUUID(), request, 0L))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void updateTripThrowsWhenCallerIsNotTheOrganizer() {
        Trip trip = publishedTrip();
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        var request = new UpdateTripRequest(null, null, null, null, null, null, null, null, null, null, "New meeting point", null, null);

        assertThatThrownBy(() -> tripService.updateTrip(UUID.randomUUID(), UUID.randomUUID(), request, 0L))
                .isInstanceOf(ForbiddenException.class);
    }

    private TripImage mockImage(boolean primary) {
        return TripImage.of(UUID.randomUUID(), "https://example.com/existing.jpg", (short) 0, primary);
    }

    private Trip publishedTrip() {
        Trip trip = Trip.newCommunityDraft(organizerId, destinationId, "Trip Title Here", "A full description.", false,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), null, null);
        trip.publish();
        return trip;
    }
}
