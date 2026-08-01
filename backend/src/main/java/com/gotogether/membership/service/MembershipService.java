package com.gotogether.membership.service;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.chat.service.ChatService;
import com.gotogether.membership.dto.AdmissionResult;
import com.gotogether.membership.dto.MarkAttendanceRequest;
import com.gotogether.membership.dto.MembershipCompletionStats;
import com.gotogether.membership.dto.RemoveMemberRequest;
import com.gotogether.membership.dto.RosterMemberResponse;
import com.gotogether.membership.dto.TripMemberResponse;
import com.gotogether.membership.entity.AttendanceStatus;
import com.gotogether.membership.entity.MembershipStatus;
import com.gotogether.membership.entity.TripMember;
import com.gotogether.membership.repository.TripMemberRepository;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.dto.TripDetailsResponse.MemberPreview;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.user.entity.AccountRole;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The membership module's only entry point for other modules — everything
 * else ({@code trip_members} entity/repository) is package-private to this
 * module in practice (enforced by {@code ArchitectureTest}).
 *
 * <p>Depends one-directionally on {@code trip} (via {@link TripService}) to
 * drive the capacity-triggered lifecycle transitions Chapter 3 Section 3.2
 * assigns to membership activity — never the reverse, which would create a
 * circular module dependency ({@code trip} cannot also depend on {@code
 * membership}; see {@code TripSummary#withJoinedCount}'s doc for how live
 * joined-counts are layered onto trip-module DTOs instead, at the controller
 * layer). {@code joinrequest} depends on this module (to admit accepted/
 * promoted requesters), not the other way around — see {@code
 * JoinRequestService}'s class doc. Also depends one-directionally on {@code
 * chat} (to archive the Trip Chat on completion, Chapter 3 Section 3.5) —
 * safe since {@code chat} only depends on {@code trip}, never on {@code
 * membership}, so no cycle results; see {@code ChatService}'s class doc.
 */
@Service
public class MembershipService {

    private final TripMemberRepository tripMemberRepository;
    private final TripService tripService;
    private final ProfileService profileService;
    private final ChatService chatService;

    public MembershipService(
            TripMemberRepository tripMemberRepository, TripService tripService, ProfileService profileService, ChatService chatService) {
        this.tripMemberRepository = tripMemberRepository;
        this.tripService = tripService;
        this.profileService = profileService;
        this.chatService = chatService;
    }

    /**
     * Atomically checks capacity and either inserts a {@code trip_members}
     * row or reports that the trip filled up first (API Spec Section 23's
     * flagged race). Callers must be {@code @Transactional} themselves so
     * {@link TripService#lockForCapacityChange}'s row lock spans this whole
     * check-then-insert operation — see that method's doc.
     */
    @Transactional
    public AdmissionResult admitOrWaitlist(UUID tripId, UUID applicantId, UUID joinRequestId) {
        ensureOrganizerSeat(tripId);
        TripCapacityInfo info = tripService.lockForCapacityChange(tripId);
        long activeCount = tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED);
        if (activeCount >= info.maxGroupSize()) {
            return new AdmissionResult(false, null);
        }
        TripMember member = tripMemberRepository.save(TripMember.fromAcceptedRequest(tripId, applicantId, joinRequestId));
        tripService.updateCapacityStatus(tripId, activeCount + 1);
        return new AdmissionResult(true, toResponse(member));
    }

    public long countActiveMembers(UUID tripId) {
        ensureOrganizerSeat(tripId);
        return tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED);
    }

    /**
     * Whether {@code userId} currently holds an active ({@code JOINED}) seat
     * on {@code tripId}. Added for {@code JoinRequestService#getJoinStatus} —
     * a Join Request's own status stays {@code ACCEPTED} forever once
     * granted (this module never writes back to {@code join_requests}, by
     * design — see this class's doc on dependency direction), so after a
     * Leave/Remove the request row alone can no longer answer "is this
     * person still on the trip." Non-throwing by design, unlike {@link
     * #getActiveMemberOrThrow}, since a status check is expected to handle
     * "not a member" as a normal outcome, not an error.
     */
    public boolean isActiveMember(UUID tripId, UUID userId) {
        return tripMemberRepository.findByTripIdAndUserId(tripId, userId).map(TripMember::isActive).orElse(false);
    }

    /**
     * Whether {@code userId} ever held a {@code trip_members} row (any
     * status) for {@code tripId} — used by {@code review} to gate Review
     * eligibility (Chapter 3 Section 3.7/3.13: "both users share Accepted
     * Membership on the Completed trip"). Deliberately status-agnostic
     * (unlike {@link #isActiveMember}): someone who was Removed or Left early
     * should still be reviewable/able-to-review, since the six review
     * sub-scores (Reliability in particular) exist partly to capture exactly
     * that kind of behaviour.
     */
    public boolean wasMember(UUID tripId, UUID userId) {
        return tripMemberRepository.existsByTripIdAndUserId(tripId, userId);
    }

    /** When {@code userId}'s own {@code trip_members} row on {@code tripId} reached Completed — anchors the {@code review} module's per-pair 14-day review window (Chapter 3 Section 3.7). Empty if the row never reached Completed (e.g. they Left/were Removed before the trip finished). */
    public Optional<OffsetDateTime> getCompletedAt(UUID tripId, UUID userId) {
        return tripMemberRepository.findByTripIdAndUserId(tripId, userId).map(TripMember::getCompletedAt);
    }

    /**
     * Aggregate trip-completion behaviour across every trip {@code userId}
     * has ever concluded (as organizer or regular member) — feeds the {@code
     * trust} module's "Trip completion behaviour" component (20% weight,
     * Trust & Discovery Module A). {@code lateLeaves} counts a voluntary Left
     * within 48 hours of the trip's departure date (Chapter 3 Section 3.4's
     * own text: "a Left within 48 hours of departure... should negatively
     * affect the member's reliability sub-score"); anything earlier is a
     * {@code gracefulLeave} (neutral). Determining "late" requires each
     * trip's {@code start_date} — fetched one-by-one via {@code TripService}
     * (N+1, acceptable at MVP scale, same trade-off already made by {@code
     * TripService.explore}'s in-memory duration filter).
     */
    public MembershipCompletionStats getCompletionStats(UUID userId) {
        List<TripMember> concluded = tripMemberRepository.findByUserIdAndStatusInOrderByJoinedAtDesc(
                userId, List.of(MembershipStatus.COMPLETED, MembershipStatus.LEFT, MembershipStatus.REMOVED));
        int completed = 0;
        int removed = 0;
        int lateLeaves = 0;
        int gracefulLeaves = 0;
        int noShows = 0;
        for (TripMember member : concluded) {
            switch (member.getStatus()) {
                // NO_SHOW is only ever set after the trip is already
                // Completed (MembershipService#markAttendance's own guard),
                // so this member's status here is still COMPLETED — the
                // branch is on attendanceStatus, not a different
                // MembershipStatus value. A null attendanceStatus (organizer
                // never marked it) is treated as attended, not penalized —
                // an unmarked trip shouldn't cost the traveller a Trust
                // Score hit for something outside their control.
                case COMPLETED -> {
                    if (member.getAttendanceStatus() == AttendanceStatus.NO_SHOW) {
                        noShows++;
                    } else {
                        completed++;
                    }
                }
                case REMOVED -> removed++;
                case LEFT -> {
                    TripSummary trip = tripService.getSummary(member.getTripId());
                    OffsetDateTime lateThreshold = trip.startDate().atStartOfDay(ZoneOffset.UTC).minusHours(48).toOffsetDateTime();
                    if (member.getLeftAt() != null && !member.getLeftAt().isBefore(lateThreshold)) {
                        lateLeaves++;
                    } else {
                        gracefulLeaves++;
                    }
                }
                default -> {
                    // JOINED can't appear here (excluded from the status filter above).
                }
            }
        }
        return new MembershipCompletionStats(completed, removed, lateLeaves, gracefulLeaves, noShows);
    }

    /** Every {@code trip_members} row for a trip, any status — used by controller-layer composition to fan out Trust Score recalculation to everyone a just-Completed trip concluded for (see {@code MembershipController#complete}). */
    public List<UUID> getAllMemberIds(UUID tripId) {
        return tripMemberRepository.findByTripIdAndStatus(tripId, MembershipStatus.COMPLETED).stream().map(TripMember::getUserId).toList();
    }

    /**
     * Bulk variant for list/explore/recommended card overlays (see {@code
     * TripController}) — deliberately does not lazily create missing
     * organizer seats (that would mean one extra write per card on every
     * list render); a trip whose organizer seat hasn't been created yet
     * simply shows a temporarily-low count that self-corrects the first time
     * anyone hits an endpoint that does call {@link #ensureOrganizerSeat}.
     */
    public Map<UUID, Integer> countActiveMembersByTripIds(List<UUID> tripIds) {
        return tripIds.stream().distinct()
                .collect(Collectors.toMap(id -> id, id -> (int) tripMemberRepository.countByTripIdAndStatus(id, MembershipStatus.JOINED)));
    }

    @Transactional
    public void leave(UUID actingUserId, UUID tripId) {
        ensureOrganizerSeat(tripId);
        TripMember member = getActiveMemberOrThrow(tripId, actingUserId);
        if (member.isOrganizer()) {
            throw new ConflictException("The organizer cannot leave a trip — cancel it instead.");
        }
        member.leave();
        tripMemberRepository.save(member);
        recomputeCapacityAfterDeparture(tripId);
    }

    @Transactional
    public TripMemberResponse removeMember(UUID actingUserId, AccountRole actingRole, UUID tripId, UUID targetUserId, RemoveMemberRequest request) {
        ensureOrganizerSeat(tripId);
        TripCapacityInfo info = tripService.getCapacityInfo(tripId);
        boolean isModerator = actingRole == AccountRole.MODERATOR || actingRole == AccountRole.ADMIN;
        if (!info.organizerId().equals(actingUserId) && !isModerator) {
            throw new ForbiddenException("Only the organizer or a moderator can remove a member.");
        }
        TripMember target = getActiveMemberOrThrow(tripId, targetUserId);
        if (target.isOrganizer()) {
            throw new ConflictException("The organizer cannot be removed from their own trip.");
        }
        target.remove(request.reason());
        tripMemberRepository.save(target);
        recomputeCapacityAfterDeparture(tripId);
        return toResponse(target);
    }

    @Transactional
    public TripMemberResponse markAttendance(UUID actingUserId, UUID tripId, UUID targetUserId, MarkAttendanceRequest request) {
        TripCapacityInfo info = tripService.getCapacityInfo(tripId);
        if (!info.organizerId().equals(actingUserId)) {
            throw new ForbiddenException("Only the organizer can record attendance.");
        }
        if (info.status() != TripStatus.COMPLETED) {
            throw new ConflictException("Attendance can only be recorded once the trip is Completed.");
        }
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, targetUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Trip member", targetUserId));
        member.setAttendanceStatus(request.attendanceStatus());
        tripMemberRepository.save(member);
        return toResponse(member);
    }

    /** {@code POST /trips/{id}/complete} (API Spec Section 9) — delegates the Trip-side transition/guards to {@code TripService}, then closes out membership for everyone still Joined. */
    @Transactional
    public TripResponse completeTrip(UUID actingUserId, UUID tripId) {
        TripResponse response = tripService.markCompleted(actingUserId, tripId);
        closeOutMembershipFor(tripId);
        return response;
    }

    /**
     * System-triggered counterpart of {@link #completeTrip} — called only
     * from {@code TripLifecycleScheduler}, no organizer to check. Shares
     * {@link #closeOutMembershipFor} with the manual path so "mark everyone
     * still Joined as Completed, archive Chat" only exists in one place.
     */
    @Transactional
    public TripResponse completeTripSystem(UUID tripId) {
        TripResponse response = tripService.systemMarkCompleted(tripId);
        closeOutMembershipFor(tripId);
        return response;
    }

    private void closeOutMembershipFor(UUID tripId) {
        List<TripMember> stillJoined = tripMemberRepository.findByTripIdAndStatus(tripId, MembershipStatus.JOINED);
        stillJoined.forEach(TripMember::markCompleted);
        tripMemberRepository.saveAll(stillJoined);
        // Chapter 3 Section 3.5 / Cross-Module Rules: Chat Active -> Archived the instant a trip reaches Completed.
        // Safe to call directly (not controller composition) — chat depends on trip, and this class already
        // depends on trip too, so membership -> chat -> trip introduces no cycle (see ChatService's class doc).
        chatService.archiveForTrip(tripId);
    }

    /**
     * {@code GET /trips/{id}/members} (API Spec Section 6) — full roster,
     * organizer first. Includes both JOINED (active trip) and COMPLETED
     * (concluded trip) — narrowed to JOINED only until the Manage Attendance
     * screen needed this same endpoint to still return something once a
     * trip's Completed and every member's status has already flipped to
     * COMPLETED (see {@code TripLifecycleScheduler}/{@code
     * MembershipService#closeOutMembershipFor}).
     */
    public List<RosterMemberResponse> getRoster(UUID tripId) {
        ensureOrganizerSeat(tripId);
        return tripMemberRepository
                .findByTripIdAndStatusInOrderByJoinedAtAsc(tripId, List.of(MembershipStatus.JOINED, MembershipStatus.COMPLETED))
                .stream()
                .map(this::toRosterResponse)
                .toList();
    }

    /** Trip Details' short "who else is going" preview (Section 6's {@code members_preview}) — organizer excluded since they're already shown in their own section. */
    public List<MemberPreview> getRosterPreview(UUID tripId, int limit) {
        ensureOrganizerSeat(tripId);
        return tripMemberRepository.findByTripIdAndStatusOrderByJoinedAtAsc(tripId, MembershipStatus.JOINED).stream()
                .filter(m -> !m.isOrganizer())
                .limit(limit)
                .map(m -> {
                    ProfilePublicSummary profile = profileService.getPublicSummary(m.getUserId());
                    return new MemberPreview(profile.displayName(), profile.photoUrl());
                })
                .toList();
    }

    /**
     * "Upcoming" tab of My Trips (API Spec Section 6) — every trip the caller
     * holds an active seat on that hasn't reached Completed/Cancelled/Archived
     * yet, soonest-departing first. Fetches candidate memberships from this
     * module's own repository, then asks {@code TripService} for each trip's
     * presentational card — the same non-DB-join, service-layer aggregation
     * pattern {@code TripService.explore}'s in-memory duration filter already
     * established, acceptable at MVP dataset sizes (Chapter 1 Section 14).
     */
    public List<TripSummary> getUpcomingTrips(UUID userId) {
        return tripSummariesFor(userId, List.of(MembershipStatus.JOINED)).stream()
                .filter(t -> t.status() != TripStatus.COMPLETED && t.status() != TripStatus.CANCELLED && t.status() != TripStatus.ARCHIVED)
                .sorted(Comparator.comparing(TripSummary::startDate))
                .toList();
    }

    /** "Past" tab of My Trips — only trips that actually reached Completed; Cancelled trips aren't shown in either tab at MVP (no dedicated tab for them in the approved My Trips design). */
    public List<TripSummary> getPastTrips(UUID userId) {
        return tripSummariesFor(userId, List.of(MembershipStatus.JOINED, MembershipStatus.COMPLETED)).stream()
                .filter(t -> t.status() == TripStatus.COMPLETED)
                .sorted(Comparator.comparing(TripSummary::endDate).reversed())
                .toList();
    }

    // --- internal helpers ---------------------------------------------------

    /**
     * Lazily creates the Organizer's own {@code trip_members} row the first
     * time membership data for a trip is touched. Trip creation happens
     * entirely inside {@code trip} module (before {@code membership} existed
     * as a concept it could call into), so there's no single "the trip was
     * just published" hook this module can react to without creating the
     * exact circular dependency this class's doc explains avoiding. Idempotent.
     */
    private void ensureOrganizerSeat(UUID tripId) {
        if (!tripMemberRepository.existsByTripIdAndOrganizerTrue(tripId)) {
            TripCapacityInfo info = tripService.getCapacityInfo(tripId);
            tripMemberRepository.save(TripMember.organizerSeat(tripId, info.organizerId()));
        }
    }

    private void recomputeCapacityAfterDeparture(UUID tripId) {
        long activeCount = tripMemberRepository.countByTripIdAndStatus(tripId, MembershipStatus.JOINED);
        tripService.updateCapacityStatus(tripId, activeCount);
    }

    private TripMember getActiveMemberOrThrow(UUID tripId, UUID userId) {
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Trip member", userId));
        if (!member.isActive()) {
            throw new UnprocessableEntityException("This user is not currently an active member of this trip.");
        }
        return member;
    }

    private List<TripSummary> tripSummariesFor(UUID userId, List<MembershipStatus> statuses) {
        return tripMemberRepository.findByUserIdAndStatusInOrderByJoinedAtDesc(userId, statuses).stream()
                .map(m -> tripService.getSummary(m.getTripId()))
                .toList();
    }

    private RosterMemberResponse toRosterResponse(TripMember member) {
        ProfilePublicSummary profile = profileService.getPublicSummary(member.getUserId());
        return new RosterMemberResponse(
                member.getUserId(), profile.displayName(), profile.photoUrl(), member.isOrganizer(), member.getJoinedAt(),
                null, member.getAttendanceStatus());
    }

    private TripMemberResponse toResponse(TripMember m) {
        return new TripMemberResponse(
                m.getId(), m.getTripId(), m.getUserId(), m.getStatus(), m.isOrganizer(), m.getAttendanceStatus(),
                m.getJoinedAt(), m.getLeftAt(), m.getRemovedAt(), m.getRemovedReason(), m.getCompletedAt());
    }
}
