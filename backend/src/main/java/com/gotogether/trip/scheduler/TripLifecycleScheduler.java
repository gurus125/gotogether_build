package com.gotogether.trip.scheduler;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.notification.service.NotificationService;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.service.TrustService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled job {@code Trip#complete()}'s own doc has pointed to as "a
 * separate Phase 9 concern" since Phase 2 — until now, nothing actually
 * moved a trip through {@code InProgress}/{@code Completed} on its own, so
 * every trip in the system sat frozen at whatever status Join Request/
 * Membership activity last left it in, and the manual {@code
 * POST /trips/{id}/complete} endpoint was unreachable (it requires {@code
 * InProgress}, which nothing ever set). That in turn meant "Travel stats"
 * COMPLETED counts, My Trips' Past tab, and the Trust Score completion
 * component never had real data to work with.
 *
 * <p>Runs once a day (03:00 server time — deliberately off-peak, arbitrary
 * beyond that). Two independent sweeps, in order:
 * <ol>
 *   <li>Every non-Draft, non-terminal trip whose {@code start_date} has
 *   arrived -> {@code InProgress}. Silent — no notification/analytics, this
 *   is just a status flip nobody needs to be told about individually.
 *   <li>Every {@code InProgress} trip whose {@code end_date} has fully
 *   passed -> {@code Completed}, replicating the exact same fan-out {@code
 *   MembershipController#complete} does for the manual path (Trust Score
 *   recalculation for every member, {@code TRUST_UPDATE}/{@code
 *   REVIEW_REMINDER} notifications) plus one new addition: an {@code
 *   ATTENDANCE_REMINDER} to the organizer, since attendance marking
 *   (ATTENDED/NO_SHOW) existed in the schema and API but nothing ever
 *   prompted anyone to actually use it.
 * </ol>
 *
 * <p>Each trip is processed in its own try/catch — one trip failing (e.g. a
 * concurrent cancellation racing the sweep) must not abort the whole day's
 * run for every other trip.
 */
@Component
public class TripLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(TripLifecycleScheduler.class);

    private final TripService tripService;
    private final MembershipService membershipService;
    private final TrustService trustService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;

    public TripLifecycleScheduler(
            TripService tripService, MembershipService membershipService, TrustService trustService,
            NotificationService notificationService, AnalyticsService analyticsService) {
        this.tripService = tripService;
        this.membershipService = membershipService;
        this.trustService = trustService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        startEligibleTrips();
        completeEligibleTrips();
    }

    private void startEligibleTrips() {
        for (UUID tripId : tripService.findTripIdsReadyToStart()) {
            try {
                tripService.systemMarkInProgress(tripId);
            } catch (Exception e) {
                log.warn("TripLifecycleScheduler: failed to start trip {}", tripId, e);
            }
        }
    }

    private void completeEligibleTrips() {
        for (UUID tripId : tripService.findTripIdsReadyToComplete()) {
            try {
                completeOneTrip(tripId);
            } catch (Exception e) {
                log.warn("TripLifecycleScheduler: failed to complete trip {}", tripId, e);
            }
        }
    }

    /**
     * Mirrors {@code MembershipController#complete}'s composition exactly
     * (trust recalculation + notifications fanned out to every member, plus
     * analytics), since this is the same event just triggered by the clock
     * instead of an organizer's tap — the two paths should look identical to
     * every other module reacting to "a trip completed."
     */
    private void completeOneTrip(UUID tripId) {
        TripResponse response = membershipService.completeTripSystem(tripId);
        analyticsService.record("trip_completed", null, ReferencedEntityType.TRIPS.tableName(), tripId, null);

        for (UUID memberId : membershipService.getAllMemberIds(tripId)) {
            trustService.recalculateForTripCompleted(tripId, memberId);
            analyticsService.record("trust_score_updated", memberId, ReferencedEntityType.TRIPS.tableName(), tripId, null);
            notificationService.create(
                    memberId, null, "TRUST_UPDATE", ReferencedEntityType.TRIPS.tableName(), tripId,
                    "Trust score updated", "Completing " + response.title() + " just updated your Trust Score.", "low");
            notificationService.create(
                    memberId, null, "REVIEW_REMINDER", ReferencedEntityType.TRIPS.tableName(), tripId,
                    "Rate your fellow travellers", "How was " + response.title() + "? Leave a review for the people you travelled with.", "medium");
        }

        notificationService.create(
                response.organizerId(), null, "ATTENDANCE_REMINDER", ReferencedEntityType.TRIPS.tableName(), tripId,
                "Mark attendance",
                response.title() + " has ended — mark who actually made it so Trust Scores reflect real attendance, not just who joined.",
                "medium");
    }
}
