package com.gotogether.joinrequest.controller;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.joinrequest.dto.CreateJoinRequestRequest;
import com.gotogether.joinrequest.dto.JoinRequestAcceptResponse;
import com.gotogether.joinrequest.dto.JoinRequestResponse;
import com.gotogether.joinrequest.dto.JoinStatusResponse;
import com.gotogether.joinrequest.dto.RejectJoinRequestRequest;
import com.gotogether.joinrequest.entity.JoinRequestStatus;
import com.gotogether.joinrequest.service.JoinRequestService;
import com.gotogether.notification.service.NotificationService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.service.TripService;
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
 * Join Request APIs (API Specification Section 8). Also fans out the three
 * Join-Request-shaped notification types (Chapter 1 §18 / Chapter 3 §3.9) —
 * wired directly here rather than inside {@code JoinRequestService}, since
 * {@code notification} has no outbound dependencies and therefore no cycle
 * risk either way (see {@code NotificationService}'s class doc).
 */
@RestController
public class JoinRequestController {

    private final JoinRequestService joinRequestService;
    private final TripService tripService;
    private final ProfileService profileService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;

    public JoinRequestController(
            JoinRequestService joinRequestService, TripService tripService, ProfileService profileService,
            NotificationService notificationService, AnalyticsService analyticsService) {
        this.joinRequestService = joinRequestService;
        this.tripService = tripService;
        this.profileService = profileService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/trips/{id}/join-requests")
    public ResponseEntity<JoinRequestResponse> create(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody CreateJoinRequestRequest request) {
        JoinRequestResponse response = joinRequestService.create(principal.userId(), id, request);
        TripSummary trip = tripService.getSummary(id);
        ProfilePublicSummary applicant = profileService.getPublicSummary(principal.userId());
        // entityType/entityId point at the trip, not the join request itself
        // — there's no per-join-request detail screen to deep-link to, but
        // there IS a trip-scoped "manage requests" queue, so that's the
        // actually-useful target. See `notifications_screen.dart`'s
        // `_handleTap` (mobile) for the routing this makes possible.
        notificationService.create(
                trip.organizerId(), principal.userId(), "JOIN_REQUEST_RECEIVED",
                ReferencedEntityType.TRIPS.tableName(), id, "Join request received",
                applicant.displayName() + " wants to join " + trip.title(), "medium");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/join-requests/{id}/withdraw")
    public JoinRequestResponse withdraw(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return joinRequestService.withdraw(principal.userId(), id);
    }

    /**
     * Translates a lost Accept-vs-capacity race into the documented {@code
     * 409 TRIP_FULL} response (API Spec Section 8) — the underlying request
     * is already correctly committed as Waiting List by the time this runs;
     * see {@code JoinRequestService.accept}'s doc for why that's done as a
     * return value rather than an exception from within the transaction.
     */
    @PostMapping("/join-requests/{id}/accept")
    public JoinRequestAcceptResponse accept(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        var outcome = joinRequestService.accept(principal.userId(), id);
        if (!outcome.admitted()) {
            throw new ConflictException("This trip reached its group size limit moments ago — the request has been moved to the waiting list instead.");
        }
        TripSummary trip = tripService.getSummary(outcome.joinRequest().tripId());
        notificationService.create(
                outcome.joinRequest().applicantId(), principal.userId(), "JOIN_REQUEST_ACCEPTED",
                ReferencedEntityType.TRIPS.tableName(), trip.id(), "Trip approved",
                "Your request for " + trip.title() + " was accepted", "medium");
        analyticsService.record(
                "trip_joined", outcome.joinRequest().applicantId(), ReferencedEntityType.TRIPS.tableName(), trip.id(), null);
        return new JoinRequestAcceptResponse(outcome.joinRequest(), outcome.tripMember());
    }

    @PostMapping("/join-requests/{id}/reject")
    public JoinRequestResponse reject(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody(required = false) RejectJoinRequestRequest request) {
        JoinRequestResponse response = joinRequestService.reject(principal.userId(), id, request != null ? request : new RejectJoinRequestRequest(null));
        TripSummary trip = tripService.getSummary(response.tripId());
        notificationService.create(
                response.applicantId(), principal.userId(), "JOIN_REQUEST_REJECTED",
                ReferencedEntityType.TRIPS.tableName(), trip.id(), "Join request declined",
                "Your request for " + trip.title() + " was declined", "low");
        return response;
    }

    /**
     * Overlays each applicant's real {@code displayName}/{@code photoUrl}
     * from {@link ProfileService} onto the queue {@code JoinRequestService}
     * builds alone — see {@code JoinRequestResponse#withApplicantProfile}'s
     * doc for why this composition happens here instead of inside either
     * service. Without it, the Organizer's "manage requests" screen has no
     * way to show who's actually asking to join, just a bare status pill.
     */
    @GetMapping("/trips/{id}/join-requests")
    public CursorPageResponse<JoinRequestResponse> organizerQueue(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestParam(required = false) JoinRequestStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        var page = joinRequestService.getOrganizerQueue(principal.userId(), id, status, cursor, limit);
        if (page.items().isEmpty()) {
            return page;
        }
        var withProfiles = page.items().stream()
                .map(jr -> {
                    var applicant = profileService.getPublicSummary(jr.applicantId());
                    return jr.withApplicantProfile(applicant.displayName(), applicant.photoUrl());
                })
                .toList();
        return new CursorPageResponse<>(withProfiles, page.nextCursor(), page.hasMore());
    }

    @GetMapping("/users/me/join-requests")
    public CursorPageResponse<JoinRequestResponse> myRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) JoinRequestStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return joinRequestService.getMyRequests(principal.userId(), status, cursor, limit);
    }

    @GetMapping("/trips/{id}/join-status")
    public JoinStatusResponse joinStatus(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return joinRequestService.getJoinStatus(principal.userId(), id);
    }
}
