package com.gotogether.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.chat.service.ChatService;
import com.gotogether.membership.dto.MarkAttendanceRequest;
import com.gotogether.membership.dto.RemoveMemberRequest;
import com.gotogether.membership.entity.AttendanceStatus;
import com.gotogether.membership.entity.MembershipStatus;
import com.gotogether.membership.entity.TripMember;
import com.gotogether.membership.repository.TripMemberRepository;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.user.entity.AccountRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock private TripMemberRepository tripMemberRepository;
    @Mock private TripService tripService;
    @Mock private ProfileService profileService;
    @Mock private ChatService chatService;

    private MembershipService membershipService;

    private final UUID tripId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(tripMemberRepository, tripService, profileService, chatService);
        lenient().when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tripMemberRepository.existsByTripIdAndOrganizerTrue(tripId)).thenReturn(true);
    }

    private TripCapacityInfo capacityInfo(TripStatus status, short min, short max) {
        return new TripCapacityInfo(tripId, organizerId, TripKind.COMMUNITY, status, min, max);
    }

    // --- admitOrWaitlist: the concurrency-critical Accept path -------------

    @Test
    void admitOrWaitlistInsertsAMemberWhenCapacityIsAvailable() {
        when(tripService.lockForCapacityChange(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS, (short) 2, (short) 6));
        when(tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(1L);

        var result = membershipService.admitOrWaitlist(tripId, memberId, UUID.randomUUID());

        assertThat(result.admitted()).isTrue();
        assertThat(result.tripMember().userId()).isEqualTo(memberId);
    }

    /**
     * The core assertion behind API Spec Section 23's flagged race: once the
     * (mocked-here, real-lock-in-production) capacity check sees the trip
     * already at {@code max_group_size}, this method must refuse the
     * admission rather than over-book — no {@code trip_members} row is
     * inserted, and the caller ({@code JoinRequestService.accept}) is the one
     * that turns this into a Waiting List entry.
     */
    @Test
    void admitOrWaitlistRefusesAdmissionWhenTripIsAlreadyAtMaxCapacity() {
        when(tripService.lockForCapacityChange(tripId)).thenReturn(capacityInfo(TripStatus.FULL, (short) 2, (short) 6));
        when(tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(6L);

        var result = membershipService.admitOrWaitlist(tripId, memberId, UUID.randomUUID());

        assertThat(result.admitted()).isFalse();
        assertThat(result.tripMember()).isNull();
    }

    @Test
    void admitOrWaitlistNotifiesTripServiceOfTheNewCapacityCount() {
        when(tripService.lockForCapacityChange(tripId)).thenReturn(capacityInfo(TripStatus.ACCEPTING_REQUESTS, (short) 2, (short) 6));
        when(tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(1L);

        membershipService.admitOrWaitlist(tripId, memberId, UUID.randomUUID());

        org.mockito.Mockito.verify(tripService).updateCapacityStatus(tripId, 2L);
    }

    // --- isActiveMember (used by JoinRequestService#getJoinStatus) ----------

    @Test
    void isActiveMemberIsTrueForAJoinedMember() {
        TripMember member = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.of(member));

        assertThat(membershipService.isActiveMember(tripId, memberId)).isTrue();
    }

    @Test
    void isActiveMemberIsFalseOnceTheMemberHasLeft() {
        TripMember member = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        member.leave();
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.of(member));

        assertThat(membershipService.isActiveMember(tripId, memberId)).isFalse();
    }

    @Test
    void isActiveMemberIsFalseWhenNoMembershipRowExistsAtAll() {
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.empty());

        assertThat(membershipService.isActiveMember(tripId, memberId)).isFalse();
    }

    // --- leave / remove ------------------------------------------------------

    @Test
    void leaveThrowsWhenTheOrganizerTriesToLeaveTheirOwnTrip() {
        TripMember organizerRow = TripMember.organizerSeat(tripId, organizerId);
        when(tripMemberRepository.findByTripIdAndUserId(tripId, organizerId)).thenReturn(Optional.of(organizerRow));

        assertThatThrownBy(() -> membershipService.leave(organizerId, tripId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void leaveThrowsWhenCallerIsNotAnActiveMember() {
        TripMember left = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        left.leave();
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> membershipService.leave(memberId, tripId)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void leaveSucceedsAndRecomputesCapacity() {
        TripMember member = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.of(member));
        when(tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(3L);

        membershipService.leave(memberId, tripId);

        assertThat(member.getStatus()).isEqualTo(MembershipStatus.LEFT);
        org.mockito.Mockito.verify(tripService).updateCapacityStatus(tripId, 3L);
    }

    @Test
    void removeMemberThrowsWhenActingUserIsNeitherOrganizerNorModerator() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.CONFIRMED, (short) 2, (short) 6));

        assertThatThrownBy(() -> membershipService.removeMember(UUID.randomUUID(), AccountRole.INDIVIDUAL, tripId, memberId, new RemoveMemberRequest("reason")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeMemberSucceedsForAModeratorEvenWithoutOwnership() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.CONFIRMED, (short) 2, (short) 6));
        TripMember member = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.of(member));
        when(tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(2L);

        var response = membershipService.removeMember(UUID.randomUUID(), AccountRole.MODERATOR, tripId, memberId, new RemoveMemberRequest("policy violation"));

        assertThat(response.status()).isEqualTo(MembershipStatus.REMOVED);
        assertThat(response.removedReason()).isEqualTo("policy violation");
    }

    @Test
    void removeMemberThrowsWhenTargetingTheOrganizer() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.CONFIRMED, (short) 2, (short) 6));
        TripMember organizerRow = TripMember.organizerSeat(tripId, organizerId);
        when(tripMemberRepository.findByTripIdAndUserId(tripId, organizerId)).thenReturn(Optional.of(organizerRow));

        assertThatThrownBy(() -> membershipService.removeMember(organizerId, AccountRole.MODERATOR, tripId, organizerId, new RemoveMemberRequest("x")))
                .isInstanceOf(ConflictException.class);
    }

    // --- attendance / completion ---------------------------------------------

    @Test
    void markAttendanceThrowsWhenTripIsNotYetCompleted() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.IN_PROGRESS, (short) 2, (short) 6));

        assertThatThrownBy(() -> membershipService.markAttendance(organizerId, tripId, memberId, new MarkAttendanceRequest(AttendanceStatus.ATTENDED)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void markAttendanceThrowsWhenCallerIsNotTheOrganizer() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.COMPLETED, (short) 2, (short) 6));

        assertThatThrownBy(() -> membershipService.markAttendance(UUID.randomUUID(), tripId, memberId, new MarkAttendanceRequest(AttendanceStatus.NO_SHOW)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markAttendanceSucceedsOnceTripIsCompleted() {
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo(TripStatus.COMPLETED, (short) 2, (short) 6));
        TripMember member = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.of(member));

        var response = membershipService.markAttendance(organizerId, tripId, memberId, new MarkAttendanceRequest(AttendanceStatus.ATTENDED));

        assertThat(response.attendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    }

    @Test
    void completeTripMarksEveryStillJoinedMemberAsCompleted() {
        when(tripService.markCompleted(organizerId, tripId)).thenReturn(
                new TripResponse(tripId, organizerId, null, null, TripKind.COMMUNITY, TripStatus.COMPLETED, null, "t", "d", null,
                        false, null, null, null, null, null, (short) 2, (short) 6, true, true, null, null, null, null, null, List.of(), null, null));
        TripMember a = TripMember.organizerSeat(tripId, organizerId);
        TripMember b = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        when(tripMemberRepository.findByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(List.of(a, b));

        membershipService.completeTrip(organizerId, tripId);

        assertThat(a.getStatus()).isEqualTo(MembershipStatus.COMPLETED);
        assertThat(b.getStatus()).isEqualTo(MembershipStatus.COMPLETED);
    }

    // --- getCompletionStats (feeds TrustService's completion component) ------

    @Test
    void getCompletionStatsCountsANoShowSeparatelyFromCompleted() {
        TripMember attended = TripMember.fromAcceptedRequest(tripId, UUID.randomUUID(), UUID.randomUUID());
        attended.markCompleted();
        attended.setAttendanceStatus(AttendanceStatus.ATTENDED);

        TripMember noShow = TripMember.fromAcceptedRequest(tripId, UUID.randomUUID(), UUID.randomUUID());
        noShow.markCompleted();
        noShow.setAttendanceStatus(AttendanceStatus.NO_SHOW);

        when(tripMemberRepository.findByUserIdAndStatusInOrderByJoinedAtDesc(memberId,
                List.of(MembershipStatus.COMPLETED, MembershipStatus.LEFT, MembershipStatus.REMOVED)))
                .thenReturn(List.of(attended, noShow));

        var stats = membershipService.getCompletionStats(memberId);

        assertThat(stats.completed()).isEqualTo(1);
        assertThat(stats.noShows()).isEqualTo(1);
        // Both still count toward the denominator — a NO_SHOW dilutes the
        // ratio (see TrustService#completionComponent), it doesn't just
        // disappear from it.
        assertThat(stats.totalConcluded()).isEqualTo(2);
    }

    @Test
    void getCompletionStatsTreatsUnmarkedAttendanceAsCompletedNotPenalized() {
        TripMember neverMarked = TripMember.fromAcceptedRequest(tripId, UUID.randomUUID(), UUID.randomUUID());
        neverMarked.markCompleted();
        // attendanceStatus left null — organizer never called the attendance endpoint.

        when(tripMemberRepository.findByUserIdAndStatusInOrderByJoinedAtDesc(memberId,
                List.of(MembershipStatus.COMPLETED, MembershipStatus.LEFT, MembershipStatus.REMOVED)))
                .thenReturn(List.of(neverMarked));

        var stats = membershipService.getCompletionStats(memberId);

        assertThat(stats.completed()).isEqualTo(1);
        assertThat(stats.noShows()).isEqualTo(0);
    }

    @Test
    void completeTripSystemMarksEveryStillJoinedMemberAsCompletedWithoutAnOrganizerCheck() {
        when(tripService.systemMarkCompleted(tripId)).thenReturn(
                new TripResponse(tripId, organizerId, null, null, TripKind.COMMUNITY, TripStatus.COMPLETED, null, "t", "d", null,
                        false, null, null, null, null, null, (short) 2, (short) 6, true, true, null, null, null, null, null, List.of(), null, null));
        TripMember a = TripMember.organizerSeat(tripId, organizerId);
        TripMember b = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        when(tripMemberRepository.findByTripIdAndStatus(tripId, MembershipStatus.JOINED)).thenReturn(List.of(a, b));

        membershipService.completeTripSystem(tripId);

        assertThat(a.getStatus()).isEqualTo(MembershipStatus.COMPLETED);
        assertThat(b.getStatus()).isEqualTo(MembershipStatus.COMPLETED);
        // No acting-user argument at all on tripService.systemMarkCompleted(tripId) —
        // this is the whole point of the "system" variant, verified implicitly by
        // the stub above only matching the single-arg overload.
    }

    // --- roster ---------------------------------------------------------------

    @Test
    void getRosterReturnsOrganizerFirstWithNoTrustScore() {
        TripMember organizerRow = TripMember.organizerSeat(tripId, organizerId);
        when(tripMemberRepository.findByTripIdAndStatusInOrderByJoinedAtAsc(tripId, List.of(MembershipStatus.JOINED, MembershipStatus.COMPLETED)))
                .thenReturn(List.of(organizerRow));
        when(profileService.getPublicSummary(organizerId)).thenReturn(new ProfilePublicSummary(organizerId, "Maya R.", null));

        var roster = membershipService.getRoster(tripId);

        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).isOrganizer()).isTrue();
        assertThat(roster.get(0).trustScore()).isNull();
    }

    /** {@code getRoster} previously returned nothing at all for a Completed trip (JOINED-only query) — now includes COMPLETED members too, which is exactly what `AttendanceScreen` needs to have anything to show. */
    @Test
    void getRosterIncludesCompletedMembersNotJustJoined() {
        TripMember organizerRow = TripMember.organizerSeat(tripId, organizerId);
        TripMember completedMember = TripMember.fromAcceptedRequest(tripId, memberId, UUID.randomUUID());
        completedMember.markCompleted();
        when(tripMemberRepository.findByTripIdAndStatusInOrderByJoinedAtAsc(tripId, List.of(MembershipStatus.JOINED, MembershipStatus.COMPLETED)))
                .thenReturn(List.of(organizerRow, completedMember));
        when(profileService.getPublicSummary(any())).thenReturn(new ProfilePublicSummary(memberId, "Traveller", null));

        var roster = membershipService.getRoster(tripId);

        assertThat(roster).hasSize(2);
    }

    @Test
    void getActiveMemberOrThrowSurfacesResourceNotFoundForAnUnknownUser() {
        when(tripMemberRepository.findByTripIdAndUserId(tripId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.leave(memberId, tripId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
