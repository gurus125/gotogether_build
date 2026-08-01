package com.gotogether.membership.controller;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.joinrequest.service.JoinRequestService;
import com.gotogether.membership.dto.MarkAttendanceRequest;
import com.gotogether.membership.dto.RemoveMemberRequest;
import com.gotogether.membership.dto.RosterMemberResponse;
import com.gotogether.membership.dto.TripMemberResponse;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.notification.service.NotificationService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.service.TrustService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Membership APIs (API Specification Section 9) plus the roster read (Section
 * 6's {@code GET /trips/{id}/members}, which {@code TripController} never
 * implemented — see its class doc — since the data lives here).
 */
@RestController
public class MembershipController {

    private final MembershipService membershipService;
    private final JoinRequestService joinRequestService;
    private final TrustService trustService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final TripService tripService;
    private final ProfileService profileService;

    public MembershipController(
            MembershipService membershipService, JoinRequestService joinRequestService, TrustService trustService,
            NotificationService notificationService, AnalyticsService analyticsService, TripService tripService,
            ProfileService profileService) {
        this.membershipService = membershipService;
        this.joinRequestService = joinRequestService;
        this.trustService = trustService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
        this.tripService = tripService;
        this.profileService = profileService;
    }

    @GetMapping("/trips/{id}/members")
    public List<RosterMemberResponse> roster(@PathVariable UUID id) {
        return membershipService.getRoster(id);
    }

    @PostMapping("/trips/{id}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        membershipService.leave(principal.userId(), id);
        // A spot may have just opened — promote the FIFO-oldest waitlisted
        // request, if any (see JoinRequestService.promoteWaitlistIfCapacityAvailable's
        // doc for why this is a deliberate follow-up call, not part of the
        // same transaction as the Leave itself).
        joinRequestService.promoteWaitlistIfCapacityAvailable(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Notifies the removed member ({@code trip_update}) naming both the trip
     * and who removed them — previously silent, which meant someone could be
     * dropped from a trip with no indication anything happened until they
     * noticed the trip missing from "Upcoming." Mirrors {@code
     * TripController#cancel}'s identical composition (fetch the acting
     * organizer/moderator's real name via {@link ProfileService}, the trip's
     * title via {@link TripService#getSummary}, wired directly here since
     * neither {@code profile} nor {@code trip} depends back on {@code
     * membership}).
     */
    @PostMapping("/trips/{id}/members/{userId}/remove")
    public TripMemberResponse remove(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID userId,
            @Valid @RequestBody RemoveMemberRequest request) {
        TripMemberResponse response = membershipService.removeMember(principal.userId(), principal.role(), id, userId, request);
        joinRequestService.promoteWaitlistIfCapacityAvailable(id);
        TripSummary trip = tripService.getSummary(id);
        ProfilePublicSummary remover = profileService.getPublicSummary(principal.userId());
        notificationService.create(
                userId, principal.userId(), "TRIP_UPDATE",
                ReferencedEntityType.TRIPS.tableName(), id, "Removed from trip",
                "You were removed from " + trip.title() + " by " + remover.displayName() + ". Reason: " + request.reason(), "high");
        return response;
    }

    @PatchMapping("/trips/{id}/members/{userId}/attendance")
    public TripMemberResponse attendance(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID userId,
            @Valid @RequestBody MarkAttendanceRequest request) {
        return membershipService.markAttendance(principal.userId(), id, userId, request);
    }

    /**
     * Trip completion behaviour (20% weight) applies to every participant,
     * not just the Organizer (contrast {@code TripController#cancel}, which
     * only recalculates the Organizer) — so this fans Trust Score
     * recalculation out to the whole just-completed roster. Wired here
     * rather than inside {@code MembershipService.completeTrip} for the same
     * cycle-avoidance reason {@code TrustService}'s class doc explains
     * ({@code trust} depends on {@code membership}, so {@code membership}
     * cannot depend back on {@code trust}). Also notifies every member their
     * Trust Score changed ({@code trust_update}) and prompts them to review
     * their fellow travellers ({@code review_reminder}) — {@code
     * notification} has no outbound dependencies, so calling it directly
     * here (rather than composing at yet another layer) has no cycle risk.
     */
    @PostMapping("/trips/{id}/complete")
    public TripResponse complete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        TripResponse response = membershipService.completeTrip(principal.userId(), id);
        analyticsService.record("trip_completed", principal.userId(), ReferencedEntityType.TRIPS.tableName(), id, null);
        membershipService.getAllMemberIds(id).forEach(memberId -> {
            trustService.recalculateForTripCompleted(id, memberId);
            analyticsService.record("trust_score_updated", memberId, ReferencedEntityType.TRIPS.tableName(), id, null);
            notificationService.create(
                    memberId, null, "TRUST_UPDATE", ReferencedEntityType.TRIPS.tableName(), id,
                    "Trust score updated", "Completing " + response.title() + " just updated your Trust Score.", "low");
            notificationService.create(
                    memberId, null, "REVIEW_REMINDER", ReferencedEntityType.TRIPS.tableName(), id,
                    "Rate your fellow travellers", "How was " + response.title() + "? Leave a review for the people you travelled with.", "medium");
        });
        return response;
    }
}
