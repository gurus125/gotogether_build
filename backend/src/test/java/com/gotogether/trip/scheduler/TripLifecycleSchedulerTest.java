package com.gotogether.trip.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.notification.service.NotificationService;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.service.TrustService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripLifecycleSchedulerTest {

    @Mock private TripService tripService;
    @Mock private MembershipService membershipService;
    @Mock private TrustService trustService;
    @Mock private NotificationService notificationService;
    @Mock private AnalyticsService analyticsService;

    private TripLifecycleScheduler scheduler;

    private final UUID organizerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new TripLifecycleScheduler(tripService, membershipService, trustService, notificationService, analyticsService);
    }

    private TripResponse tripResponse(UUID tripId) {
        return new TripResponse(tripId, organizerId, null, null, TripKind.COMMUNITY, TripStatus.COMPLETED, null, "Goa Trip", "d", null,
                false, null, null, null, null, null, (short) 2, (short) 6, true, true, null, null, null, null, null, List.of(), null, null);
    }

    @Test
    void startsEveryTripTheReadyToStartQueryReturns() {
        UUID tripA = UUID.randomUUID();
        UUID tripB = UUID.randomUUID();
        when(tripService.findTripIdsReadyToStart()).thenReturn(List.of(tripA, tripB));
        when(tripService.findTripIdsReadyToComplete()).thenReturn(List.of());

        scheduler.run();

        verify(tripService).systemMarkInProgress(tripA);
        verify(tripService).systemMarkInProgress(tripB);
    }

    @Test
    void oneTripFailingToStartDoesNotPreventTheOthersFromStarting() {
        UUID tripA = UUID.randomUUID();
        UUID tripB = UUID.randomUUID();
        when(tripService.findTripIdsReadyToStart()).thenReturn(List.of(tripA, tripB));
        when(tripService.findTripIdsReadyToComplete()).thenReturn(List.of());
        when(tripService.systemMarkInProgress(tripA)).thenThrow(new RuntimeException("simulated failure"));

        scheduler.run();

        verify(tripService).systemMarkInProgress(tripA);
        verify(tripService).systemMarkInProgress(tripB);
    }

    @Test
    void completingATripFansOutTrustNotificationsToEveryMemberPlusTheOrganizer() {
        UUID tripId = UUID.randomUUID();
        when(tripService.findTripIdsReadyToStart()).thenReturn(List.of());
        when(tripService.findTripIdsReadyToComplete()).thenReturn(List.of(tripId));
        when(membershipService.completeTripSystem(tripId)).thenReturn(tripResponse(tripId));
        when(membershipService.getAllMemberIds(tripId)).thenReturn(List.of(memberId));

        scheduler.run();

        verify(membershipService).completeTripSystem(tripId);
        verify(trustService).recalculateForTripCompleted(tripId, memberId);
        verify(analyticsService).record(eq("trip_completed"), any(), anyString(), eq(tripId), any());
        verify(analyticsService).record(eq("trust_score_updated"), eq(memberId), anyString(), eq(tripId), any());
        verify(notificationService).create(eq(memberId), any(), eq("TRUST_UPDATE"), anyString(), eq(tripId), anyString(), anyString(), anyString());
        verify(notificationService).create(eq(memberId), any(), eq("REVIEW_REMINDER"), anyString(), eq(tripId), anyString(), anyString(), anyString());
        // The new organizer-facing prompt this scheduler adds on top of the manual path's fan-out.
        verify(notificationService).create(eq(organizerId), any(), eq("ATTENDANCE_REMINDER"), anyString(), eq(tripId), anyString(), anyString(), anyString());
    }

    @Test
    void oneTripFailingToCompleteDoesNotPreventOthersFromCompleting() {
        UUID tripA = UUID.randomUUID();
        UUID tripB = UUID.randomUUID();
        when(tripService.findTripIdsReadyToStart()).thenReturn(List.of());
        when(tripService.findTripIdsReadyToComplete()).thenReturn(List.of(tripA, tripB));
        when(membershipService.completeTripSystem(tripA)).thenThrow(new RuntimeException("simulated failure"));
        when(membershipService.completeTripSystem(tripB)).thenReturn(tripResponse(tripB));
        when(membershipService.getAllMemberIds(tripB)).thenReturn(List.of());

        scheduler.run();

        verify(membershipService).completeTripSystem(tripA);
        verify(membershipService).completeTripSystem(tripB);
        verify(notificationService, times(1)).create(eq(organizerId), any(), eq("ATTENDANCE_REMINDER"), anyString(), eq(tripB), anyString(), anyString(), anyString());
    }
}
