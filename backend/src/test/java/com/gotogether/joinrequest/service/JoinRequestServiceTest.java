package com.gotogether.joinrequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.chat.service.ChatService;
import com.gotogether.joinrequest.dto.CreateJoinRequestRequest;
import com.gotogether.joinrequest.dto.JoinStatusResponse;
import com.gotogether.joinrequest.dto.RejectJoinRequestRequest;
import com.gotogether.joinrequest.entity.JoinRequest;
import com.gotogether.joinrequest.entity.JoinRequestStatus;
import com.gotogether.joinrequest.repository.JoinRequestRepository;
import com.gotogether.membership.dto.AdmissionResult;
import com.gotogether.membership.dto.TripMemberResponse;
import com.gotogether.membership.entity.MembershipStatus;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.time.Duration;
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
class JoinRequestServiceTest {

    @Mock private JoinRequestRepository joinRequestRepository;
    @Mock private TripService tripService;
    @Mock private MembershipService membershipService;
    @Mock private UserService userService;
    @Mock private ChatService chatService;

    private JoinRequestService joinRequestService;

    private final UUID tripId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID applicantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        joinRequestService = new JoinRequestService(joinRequestRepository, tripService, membershipService, userService, chatService);
        // @Value is only processed inside a real Spring context — a plain
        // `new JoinRequestService(...)` otherwise leaves this at Java's
        // boolean default (false), which silently no-ops requireIdApproved
        // and would make createThrowsWhenApplicantIsNotIdApproved below pass
        // vacuously. Same latent gap found and fixed in TripServiceTest.
        org.springframework.test.util.ReflectionTestUtils.setField(joinRequestService, "enforceIdApproval", true);
        lenient().when(joinRequestRepository.save(any(JoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userService.getSummary(applicantId)).thenReturn(
                new UserSummary(applicantId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
    }

    private TripCapacityInfo capacityInfo(TripStatus status) {
        return new TripCapacityInfo(tripId, organizerId, TripKind.COMMUNITY, status, (short) 2, (short) 6);
    }

    // --- create ---------------------------------------------------------------

    @Test
    void createThrowsWhenApplicantIsNotIdApproved() {
        when(userService.getSummary(applicantId)).thenReturn(
                new UserSummary(applicantId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.EMAIL));

        assertThatThrownBy(() -> joinRequestService.create(applicantId, tripId, new CreateJoinRequestRequest(null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createThrowsWhenTripIsNotJoinable() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.DRAFT));

        assertThatThrownBy(() -> joinRequestService.create(applicantId, tripId, new CreateJoinRequestRequest(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createThrowsOnSelfJoin() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.PUBLISHED));

        assertThatThrownBy(() -> joinRequestService.create(organizerId, tripId, new CreateJoinRequestRequest(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createThrowsWhenAnOpenRequestAlreadyExists() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.PUBLISHED));
        when(joinRequestRepository.findByApplicantIdAndTripIdAndStatusIn(applicantId, tripId, List.of(JoinRequestStatus.PENDING, JoinRequestStatus.WAITING_LIST)))
                .thenReturn(Optional.of(JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5))));

        assertThatThrownBy(() -> joinRequestService.create(applicantId, tripId, new CreateJoinRequestRequest(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createThrowsWhileWithinTheRejectCooldownWindow() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.PUBLISHED));
        when(joinRequestRepository.findByApplicantIdAndTripIdAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        // reject() stamps decidedAt = now(), so the 7-day cooldown has just started — this is the "still active" case.
        JoinRequest justRejected = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        justRejected.reject("not a fit");
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId)).thenReturn(Optional.of(justRejected));

        assertThatThrownBy(() -> joinRequestService.create(applicantId, tripId, new CreateJoinRequestRequest(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createSucceedsAsPendingWhenTheTripHasRoom() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS));
        when(joinRequestRepository.findByApplicantIdAndTripIdAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());

        var response = joinRequestService.create(applicantId, tripId, new CreateJoinRequestRequest("Excited to join!"));

        assertThat(response.status()).isEqualTo(JoinRequestStatus.PENDING);
        verify(tripService).onFirstJoinRequest(tripId);
    }

    @Test
    void createRoutesToWaitingListWhenTheTripIsFull() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.FULL));
        when(joinRequestRepository.findByApplicantIdAndTripIdAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(joinRequestRepository.countByTripIdAndStatus(tripId, JoinRequestStatus.WAITING_LIST)).thenReturn(2L);

        var response = joinRequestService.create(applicantId, tripId, new CreateJoinRequestRequest(null));

        assertThat(response.status()).isEqualTo(JoinRequestStatus.WAITING_LIST);
        assertThat(response.waitlistPosition()).isEqualTo(3);
    }

    // --- withdraw ---------------------------------------------------------------

    @Test
    void withdrawThrowsWhenCallerIsNotTheApplicant() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));

        assertThatThrownBy(() -> joinRequestService.withdraw(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void withdrawThrowsWhenAlreadyDecided() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        jr.accept();
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));

        assertThatThrownBy(() -> joinRequestService.withdraw(applicantId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void withdrawSucceedsForAnOpenPendingRequest() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));

        var response = joinRequestService.withdraw(applicantId, UUID.randomUUID());

        assertThat(response.status()).isEqualTo(JoinRequestStatus.WITHDRAWN);
    }

    // --- accept: the concurrency-sensitive path -----------------------------

    @Test
    void acceptThrowsWhenCallerIsNotTheOrganizer() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS));

        assertThatThrownBy(() -> joinRequestService.accept(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptThrowsWhenTheTripIsCancelled() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.CANCELLED));

        assertThatThrownBy(() -> joinRequestService.accept(organizerId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptThrowsWhenTheRequestIsNotPending() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        jr.withdraw();
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));

        assertThatThrownBy(() -> joinRequestService.accept(organizerId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptSucceedsAndMarksTheRequestAccepted() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS));
        TripMemberResponse memberResponse = new TripMemberResponse(UUID.randomUUID(), tripId, applicantId, MembershipStatus.JOINED, false, null, OffsetDateTime.now(), null, null, null, null);
        when(membershipService.admitOrWaitlist(tripId, applicantId, jr.getId())).thenReturn(new AdmissionResult(true, memberResponse));

        var outcome = joinRequestService.accept(organizerId, UUID.randomUUID());

        assertThat(outcome.admitted()).isTrue();
        assertThat(outcome.joinRequest().status()).isEqualTo(JoinRequestStatus.ACCEPTED);
        assertThat(outcome.tripMember()).isSameAs(memberResponse);
    }

    /**
     * The exact scenario API Spec Section 23 flags: capacity fills between
     * the Organizer opening the request and tapping Accept. {@code
     * MembershipService} (mocked here) is what actually re-checks capacity
     * under a real DB lock in production — this test verifies {@code
     * JoinRequestService} correctly turns a refusal into a persisted Waiting
     * List entry (not an exception, not a silently-lost request) and reports
     * {@code admitted = false} so the controller returns 409 TRIP_FULL.
     */
    @Test
    void acceptMovesTheRequestToWaitingListWhenMembershipRefusesTheRace() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.FULL));
        when(membershipService.admitOrWaitlist(tripId, applicantId, jr.getId())).thenReturn(new AdmissionResult(false, null));
        when(joinRequestRepository.countByTripIdAndStatus(tripId, JoinRequestStatus.WAITING_LIST)).thenReturn(0L);

        var outcome = joinRequestService.accept(organizerId, UUID.randomUUID());

        assertThat(outcome.admitted()).isFalse();
        assertThat(outcome.joinRequest().status()).isEqualTo(JoinRequestStatus.WAITING_LIST);
        assertThat(outcome.joinRequest().waitlistPosition()).isEqualTo(1);
    }

    // --- reject -----------------------------------------------------------------

    @Test
    void rejectThrowsWhenCallerIsNotTheOrganizer() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS));

        assertThatThrownBy(() -> joinRequestService.reject(UUID.randomUUID(), UUID.randomUUID(), new RejectJoinRequestRequest(null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectSucceedsWithAnOptionalNote() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        when(joinRequestRepository.findById(any())).thenReturn(Optional.of(jr));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS));

        var response = joinRequestService.reject(organizerId, UUID.randomUUID(), new RejectJoinRequestRequest("group is full of close friends"));

        assertThat(response.status()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(response.organizerResponseNote()).isEqualTo("group is full of close friends");
    }

    // --- join status --------------------------------------------------------

    @Test
    void getJoinStatusReturnsNotRequestedWhenNoRequestExists() {
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId)).thenReturn(Optional.empty());

        var status = joinRequestService.getJoinStatus(applicantId, tripId);

        assertThat(status.status()).isEqualTo(JoinStatusResponse.NOT_REQUESTED);
    }

    @Test
    void getJoinStatusReportsCanReapplyAtWhileTheCooldownIsStillActive() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        jr.reject(null); // decidedAt = now(), so the 7-day cooldown has just started
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId)).thenReturn(Optional.of(jr));

        var status = joinRequestService.getJoinStatus(applicantId, tripId);

        assertThat(status.status()).isEqualTo("REJECTED");
        assertThat(status.canReapplyAt()).isNotNull();
    }

    @Test
    void getJoinStatusOmitsCanReapplyAtOnceTheCooldownHasPassed() throws Exception {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        jr.reject(null);
        // reject() always stamps decidedAt = now(); back-date it via reflection (no
        // public setter exists, deliberately — see JoinRequest's class doc) to
        // simulate a decision made well over 7 days ago.
        var field = JoinRequest.class.getDeclaredField("decidedAt");
        field.setAccessible(true);
        field.set(jr, OffsetDateTime.now().minusDays(10));
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId)).thenReturn(Optional.of(jr));

        var status = joinRequestService.getJoinStatus(applicantId, tripId);

        assertThat(status.status()).isEqualTo("REJECTED");
        assertThat(status.canReapplyAt()).isNull();
    }

    @Test
    void getJoinStatusReportsNotRequestedWhenAnAcceptedMemberHasSinceLeft() {
        // The join_request row itself never changes on Leave (membership doesn't
        // write back to join_requests — see MembershipService#isActiveMember's
        // doc), so this exercises the getJoinStatus-side check that catches it.
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        jr.accept();
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId)).thenReturn(Optional.of(jr));
        when(membershipService.isActiveMember(tripId, applicantId)).thenReturn(false);

        var status = joinRequestService.getJoinStatus(applicantId, tripId);

        assertThat(status.status()).isEqualTo(JoinStatusResponse.NOT_REQUESTED);
        assertThat(status.joinRequestId()).isNull();
    }

    @Test
    void getJoinStatusReportsAcceptedWhileStillAnActiveMember() {
        JoinRequest jr = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        jr.accept();
        when(joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId)).thenReturn(Optional.of(jr));
        when(membershipService.isActiveMember(tripId, applicantId)).thenReturn(true);

        var status = joinRequestService.getJoinStatus(applicantId, tripId);

        assertThat(status.status()).isEqualTo("ACCEPTED");
    }

    // --- waitlist promotion ---------------------------------------------------

    @Test
    void promoteWaitlistPromotesTheOldestWaitlistedRequestWhenCapacityOpens() {
        when(tripService.lockForCapacityChange(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS));
        when(membershipService.countActiveMembers(tripId)).thenReturn(1L, 2L);
        JoinRequest waiting = JoinRequest.create(applicantId, tripId, null, null, Duration.ofDays(5));
        waiting.moveToWaitingList(1);
        when(joinRequestRepository.findFirstByTripIdAndStatusOrderByCreatedAtAsc(tripId, JoinRequestStatus.WAITING_LIST))
                .thenReturn(Optional.of(waiting), Optional.empty());
        TripMemberResponse memberResponse = new TripMemberResponse(UUID.randomUUID(), tripId, applicantId, MembershipStatus.JOINED, false, null, OffsetDateTime.now(), null, null, null, null);
        when(membershipService.admitOrWaitlist(tripId, applicantId, waiting.getId())).thenReturn(new AdmissionResult(true, memberResponse));

        joinRequestService.promoteWaitlistIfCapacityAvailable(tripId);

        assertThat(waiting.getStatus()).isEqualTo(JoinRequestStatus.ACCEPTED);
        verify(joinRequestRepository, times(1)).save(waiting);
    }

    @Test
    void promoteWaitlistDoesNothingWhenTheTripIsAlreadyAtCapacity() {
        when(tripService.lockForCapacityChange(tripId)).thenReturn(capacityInfo(TripStatus.FULL));
        when(membershipService.countActiveMembers(tripId)).thenReturn(6L);

        joinRequestService.promoteWaitlistIfCapacityAvailable(tripId);

        verify(joinRequestRepository, times(0)).findFirstByTripIdAndStatusOrderByCreatedAtAsc(any(), any());
    }
}
