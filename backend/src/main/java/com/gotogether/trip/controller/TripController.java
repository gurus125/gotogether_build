package com.gotogether.trip.controller;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.chat.service.ChatService;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.joinrequest.service.JoinRequestService;
import com.gotogether.membership.dto.MembershipCompletionStats;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.notification.service.NotificationService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.storage.dto.PresignedUploadResponse;
import com.gotogether.storage.service.StorageService;
import com.gotogether.trip.dto.AddTripImageRequest;
import com.gotogether.trip.dto.CancelTripRequest;
import com.gotogether.trip.dto.CreateTripRequest;
import com.gotogether.trip.dto.TravelStatsResponse;
import com.gotogether.trip.dto.TripDetailsResponse;
import com.gotogether.trip.dto.TripImageResponse;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.dto.UpdateTripRequest;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.service.TrustService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trip APIs (API Specification Section 6). Image upload ({@code
 * /trips/{id}/images}) is now wired against {@code storage.StorageService}
 * (previously unimplemented — no {@code StorageService} existed, consistent
 * with Phase 1 never wiring {@code POST /users/me/photo} either; both are now
 * done, see {@code ProfileController} for the photo-upload counterpart).
 * Roster/leave/remove/attendance/complete now live in {@code
 * MembershipController} (Phase 3) since that's where the underlying data is
 * owned; this class depends on {@link MembershipService}/{@link
 * JoinRequestService} only to overlay live joined-counts and members/join-status
 * onto its own DTOs — see {@code TripSummary#withJoinedCount}'s doc for why
 * that composition happens here rather than inside {@code TripService}.
 */
@RestController
public class TripController {

    private final TripService tripService;
    private final MembershipService membershipService;
    private final JoinRequestService joinRequestService;
    private final ChatService chatService;
    private final TrustService trustService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final StorageService storageService;
    private final ProfileService profileService;

    public TripController(
            TripService tripService, MembershipService membershipService, JoinRequestService joinRequestService,
            ChatService chatService, TrustService trustService, NotificationService notificationService,
            AnalyticsService analyticsService, StorageService storageService, ProfileService profileService) {
        this.tripService = tripService;
        this.membershipService = membershipService;
        this.joinRequestService = joinRequestService;
        this.chatService = chatService;
        this.trustService = trustService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
        this.storageService = storageService;
        this.profileService = profileService;
    }

    @PostMapping("/trips")
    public ResponseEntity<TripResponse> createDraft(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateTripRequest request) {
        TripResponse response = tripService.createDraft(principal.userId(), request);
        analyticsService.record("trip_created", principal.userId(), ReferencedEntityType.TRIPS.tableName(), response.id(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * "Manage Trip" — the fields the Create Trip wizard's Review step always
     * promised were editable "right after publishing" (group size, meeting
     * point, approval/waitlist settings) but that had no endpoint actually
     * wired to them until now — see {@link UpdateTripRequest}'s class doc.
     * {@code currentActiveMembers} is resolved here (not inside {@code
     * TripService}, which never depends on {@code membership} — see that
     * class's own doc) and passed through so a {@code max_group_size}
     * shrink can be rejected if it would go below the trip's actual
     * headcount.
     */
    @PatchMapping("/trips/{id}")
    public TripResponse update(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody UpdateTripRequest request) {
        return tripService.updateTrip(principal.userId(), id, request, membershipService.countActiveMembers(id));
    }

    /**
     * Also seats the Organizer's Trip Chat room immediately on publish
     * (rather than waiting for the first Accepted Join Request) — wired here
     * rather than inside {@code TripService.publish} for the same
     * cycle-avoidance reason as {@link #cancel}; see {@code ChatService}'s
     * class doc.
     */
    @PostMapping("/trips/{id}/publish")
    public TripResponse publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        TripResponse response = tripService.publish(principal.userId(), id);
        chatService.ensureRoomExists(id);
        analyticsService.record("trip_published", principal.userId(), ReferencedEntityType.TRIPS.tableName(), id, null);
        return response;
    }

    /**
     * Cancellation immediately archives the trip's Chat for all Members
     * (Business Rules Cross-Module Rules; Chapter 3 Section 3.5), wired here
     * rather than inside {@code TripService.cancel} itself — {@code
     * ChatService} already depends on {@code TripService} (to resolve/seat
     * the organizer), so {@code trip} cannot also depend on {@code chat}
     * without creating a cycle. See {@code ChatService}'s class doc for the
     * full dependency-direction reasoning (the same composition-at-the-
     * controller-layer fix as {@code TripSummary#withJoinedCount}'s). Also
     * recalculates the Organizer's (not Members') Trust Score — Chapter 3
     * Section 3.2's own edge case: cancellation "impacts Organizer's Trust
     * Score, not Members'" — for the identical cycle-avoidance reason (
     * {@code trust} depends on {@code trip}, so {@code trip} cannot depend
     * back on {@code trust}; see {@code TrustService}'s class doc). Also
     * notifies every other member ({@code trip_update}, Phase 6) — {@code
     * notification} has no outbound dependencies, so there's no cycle risk
     * calling it directly here either. The notification body names both the
     * trip and the organizer ({@link ProfileService#getPublicSummary}
     * resolves {@code response.organizerId()} to a real display name — a
     * moderator/admin can also trigger this per {@code TripService.cancel}'s
     * own check, so this deliberately looks up the trip's actual organizer,
     * not {@code principal}, to avoid ever telling members "cancelled by"
     * the wrong person).
     */
    @PostMapping("/trips/{id}/cancel")
    public TripResponse cancel(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody CancelTripRequest request) {
        TripResponse response = tripService.cancel(principal.userId(), principal.role(), id, request);
        chatService.archiveForTrip(id);
        trustService.recalculateForTripCancelled(id, response.organizerId());
        analyticsService.record("trip_cancelled", principal.userId(), ReferencedEntityType.TRIPS.tableName(), id, null);
        analyticsService.record("trust_score_updated", response.organizerId(), ReferencedEntityType.TRIPS.tableName(), id, null);
        ProfilePublicSummary organizer = profileService.getPublicSummary(response.organizerId());
        for (var member : membershipService.getRoster(id)) {
            if (member.userId().equals(principal.userId())) continue;
            notificationService.create(
                    member.userId(), principal.userId(), "TRIP_UPDATE",
                    ReferencedEntityType.TRIPS.tableName(), id, "Trip cancelled",
                    response.title() + " has been cancelled by " + organizer.displayName() + ".", "high");
        }
        return response;
    }

    @DeleteMapping("/trips/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tripService.deleteTrip(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Step 1 of trip-photo upload — same presigned-URL pattern as {@code ProfileController#createPhotoUploadUrl}, keyed under {@code trip-images/{tripId}} instead of a user id. Organizer-only, checked here (before any S3 call) rather than only at the eventual {@link #addImage}. */
    @PostMapping("/trips/{id}/images/upload-url")
    public PresignedUploadResponse createImageUploadUrl(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestParam("content_type") String contentType) {
        tripService.assertOrganizer(principal.userId(), id);
        return storageService.createPresignedImageUploadUrl("trip-images/" + id, contentType);
    }

    /** Step 2 — persists the uploaded image (its {@code public_url} from step 1) as a {@code trip_images} row. */
    @PostMapping("/trips/{id}/images")
    public ResponseEntity<TripImageResponse> addImage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody AddTripImageRequest request) {
        TripImageResponse response = tripService.addImage(principal.userId(), id, request.imageUrl(), request.isPrimary());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/trips/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID imageId) {
        tripService.deleteImage(principal.userId(), id, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trips/{id}")
    public TripDetailsResponse getDetails(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        TripDetailsResponse base = tripService.getTripDetails(principal.userId(), id);
        var membersPreview = membershipService.getRosterPreview(id, 5);
        var joinStatus = joinRequestService.getJoinStatus(principal.userId(), id).status();
        return new TripDetailsResponse(base.trip(), base.organizer(), membersPreview, base.compatibilityScore(), joinStatus);
    }

    @GetMapping("/trips")
    public CursorPageResponse<TripSummary> list(
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) TripKind kind,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return withLiveCounts(tripService.listTrips(destinationId, kind, status, cursor, limit));
    }

    /** "Trips For You" (Home Screen) — see {@code TripService#recommended}'s doc for the Chapter 4 dependency this stubs around. */
    @GetMapping("/trips/recommended")
    public CursorPageResponse<TripSummary> recommended(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return withLiveCounts(tripService.recommended(principal.userId(), cursor, limit));
    }

    /** My Trips (API Spec Section 6) — Created/Saved are pure {@code trip} data; Upcoming/Past come from {@code membership} (see {@code MembershipService}'s doc). All four get live joined-counts overlaid like every other card surface. */
    @GetMapping("/users/me/trips")
    public List<TripSummary> myTrips(@AuthenticationPrincipal UserPrincipal principal, @RequestParam(defaultValue = "upcoming") String tab) {
        List<TripSummary> items = switch (tab) {
            case "past" -> membershipService.getPastTrips(principal.userId());
            case "created" -> tripService.listOwnTrips(principal.userId());
            case "saved" -> tripService.listSavedTripsFor(principal.userId());
            default -> membershipService.getUpcomingTrips(principal.userId());
        };
        return withLiveCounts(items);
    }

    /**
     * "Travel stats" (JOINED / COMPLETED / ORGANIZED) for the Profile screen
     * (see {@code TravelStatsResponse}'s class doc — added for Phase 5's
     * Flutter build, not in the original API Specification table).
     * {@code joined} = currently-upcoming memberships plus every concluded
     * one ({@link MembershipCompletionStats#totalConcluded()} — completed,
     * removed, and both leave kinds), so a trip a member left early still
     * counts as "joined" even though it won't appear in Upcoming or Past.
     * {@code completed} is the strict "reached Completed" count, not just
     * "any past trip", so it can't be derived from the Past tab's list
     * (which is already exactly this same query — see {@code
     * MembershipService#getPastTrips}). {@code organized} reuses {@code
     * listOwnTrips} (the Created tab's own query) rather than a new count
     * query.
     */
    @GetMapping("/users/me/travel-stats")
    public TravelStatsResponse travelStats(@AuthenticationPrincipal UserPrincipal principal) {
        var stats = membershipService.getCompletionStats(principal.userId());
        int upcoming = membershipService.getUpcomingTrips(principal.userId()).size();
        int organized = tripService.listOwnTrips(principal.userId()).size();
        return new TravelStatsResponse(upcoming + stats.totalConcluded(), stats.completed(), organized);
    }

    @PostMapping("/trips/{id}/save")
    public ResponseEntity<Void> save(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tripService.saveTrip(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trips/{id}/save")
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tripService.unsaveTrip(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Overlays real {@code trip_members} counts from {@link MembershipService}
     * onto {@link TripSummary} cards built by {@link TripService} alone —
     * see {@code TripSummary#withJoinedCount}'s doc for why this composition
     * happens at the controller layer instead of inside either service.
     */
    private CursorPageResponse<TripSummary> withLiveCounts(CursorPageResponse<TripSummary> page) {
        if (page.items().isEmpty()) {
            return page;
        }
        Map<UUID, Integer> counts = membershipService.countActiveMembersByTripIds(page.items().stream().map(TripSummary::id).toList());
        List<TripSummary> updated = page.items().stream().map(t -> t.withJoinedCount(counts.getOrDefault(t.id(), 0))).toList();
        return new CursorPageResponse<>(updated, page.nextCursor(), page.hasMore());
    }

    private List<TripSummary> withLiveCounts(List<TripSummary> items) {
        if (items.isEmpty()) {
            return items;
        }
        Map<UUID, Integer> counts = membershipService.countActiveMembersByTripIds(items.stream().map(TripSummary::id).toList());
        return items.stream().map(t -> t.withJoinedCount(counts.getOrDefault(t.id(), 0))).toList();
    }
}
