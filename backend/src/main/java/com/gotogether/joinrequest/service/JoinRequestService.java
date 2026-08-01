package com.gotogether.joinrequest.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.chat.service.ChatService;
import com.gotogether.joinrequest.dto.CreateJoinRequestRequest;
import com.gotogether.joinrequest.dto.JoinRequestResponse;
import com.gotogether.joinrequest.dto.JoinStatusResponse;
import com.gotogether.joinrequest.dto.OrganizerReliabilityStats;
import com.gotogether.joinrequest.dto.RejectJoinRequestRequest;
import com.gotogether.joinrequest.entity.JoinRequest;
import com.gotogether.joinrequest.entity.JoinRequestStatus;
import com.gotogether.joinrequest.repository.JoinRequestRepository;
import com.gotogether.membership.dto.AdmissionResult;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The join-request module's only entry point for other modules — everything
 * else ({@code join_requests} entity/repository) is package-private to this
 * module in practice (enforced by {@code ArchitectureTest}).
 *
 * <p>Depends on {@code trip} (joinability/permission checks, capacity-status
 * transitions), {@code membership} (the actual admit-or-waitlist decision
 * and roster mutation), and {@code chat} (unlocking the Trip Chat the instant
 * a request reaches Accepted, Chapter 3 Section 3.5 — see {@code
 * ChatService}'s class doc for why this direct dependency doesn't create a
 * cycle) — never the reverse; see {@code MembershipService}'s class doc for
 * the full dependency-direction reasoning. As of Phase 5, {@code trust} reads
 * this module's data too (via {@link #getOrganizerReliabilityStats}) — safe
 * in the same direction-only sense, since this module never depends on
 * {@code trust} back.
 *
 * <p><b>Deferred to later phases</b> (flagged here rather than silently
 * skipped): real notifications ("new request", "You're in!", SLA-expiry
 * nudges — the {@code notification} module is Phase 6; Chat unlock itself
 * <em>is</em> wired as of Phase 4, see {@link #accept}/{@link
 * #promoteWaitlistIfCapacityAvailable});
 * "Blocked by Organizer" eligibility check (Core Features Module B mentions
 * it, but no {@code blocked_users}-shaped table exists anywhere in the DB
 * Schema — a real, unflagged documentation gap, not something this module
 * silently drops). The 5-day SLA expiry and 7-day reject cooldown are
 * enforced without a scheduled sweep job (Phase 9) — see {@link #applyLazyExpiry}.
 */
@Service
public class JoinRequestService {

    /** Chapter 3 Section 3.3's recommended default, confirmed in API Spec Section 17's {@code system/config} shape — hardcoded here since that config endpoint doesn't exist yet in this codebase. */
    private static final Duration SLA_WINDOW = Duration.ofDays(5);

    /** Business Rules Core User Features Module B: re-request cooldown after a Reject. */
    private static final int REJECT_COOLDOWN_DAYS = 7;

    private static final int MAX_MESSAGE_LENGTH = 300;

    /** Same test-only escape hatch as {@code TripService}'s field of the same name — see that doc for why this defaults safe and only flips in {@code application-dev.yml}. */
    @Value("${gotogether.verification.enforce-id-approval:true}")
    private boolean enforceIdApproval;

    /** Statuses a trip must be in for new Join Requests to be accepted at all (Cross-Module Rules: "a trip only accepts Join Requests once Published"). */
    private static final List<TripStatus> JOINABLE_STATUSES =
            List.of(TripStatus.PUBLISHED, TripStatus.ACCEPTING_REQUESTS, TripStatus.CONFIRMED, TripStatus.FULL);

    private final JoinRequestRepository joinRequestRepository;
    private final TripService tripService;
    private final MembershipService membershipService;
    private final UserService userService;
    private final ChatService chatService;

    public JoinRequestService(
            JoinRequestRepository joinRequestRepository, TripService tripService,
            MembershipService membershipService, UserService userService, ChatService chatService) {
        this.joinRequestRepository = joinRequestRepository;
        this.tripService = tripService;
        this.membershipService = membershipService;
        this.userService = userService;
        this.chatService = chatService;
    }

    @Transactional
    public JoinRequestResponse create(UUID applicantId, UUID tripId, CreateJoinRequestRequest request) {
        if (request.requestMessage() != null && request.requestMessage().length() > MAX_MESSAGE_LENGTH) {
            throw new UnprocessableEntityException("request_message must be " + MAX_MESSAGE_LENGTH + " characters or fewer.");
        }
        requireIdApproved(applicantId);

        TripCapacityInfo trip = tripService.getCapacityInfo(tripId);
        if (!JOINABLE_STATUSES.contains(trip.status())) {
            throw new ConflictException("This trip is not currently accepting join requests.");
        }
        if (trip.organizerId().equals(applicantId)) {
            throw new ConflictException("You cannot request to join your own trip.");
        }
        if (joinRequestRepository.findByApplicantIdAndTripIdAndStatusIn(applicantId, tripId, List.of(JoinRequestStatus.PENDING, JoinRequestStatus.WAITING_LIST)).isPresent()) {
            throw new ConflictException("You already have an open request for this trip.");
        }

        JoinRequest previous = joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(applicantId, tripId).orElse(null);
        UUID reopenedFromId = null;
        if (previous != null && (previous.getStatus() == JoinRequestStatus.REJECTED || previous.getStatus() == JoinRequestStatus.EXPIRED)) {
            if (previous.getStatus() == JoinRequestStatus.REJECTED) {
                OffsetDateTime cooldownEnds = previous.getDecidedAt().plusDays(REJECT_COOLDOWN_DAYS);
                if (cooldownEnds.isAfter(OffsetDateTime.now())) {
                    throw new ConflictException("You can re-request this trip after " + cooldownEnds + " (7-day cooldown following a decline).");
                }
            }
            reopenedFromId = previous.getId();
        }

        JoinRequest joinRequest = JoinRequest.create(applicantId, tripId, request.requestMessage(), reopenedFromId, SLA_WINDOW);
        if (trip.status() == TripStatus.FULL) {
            // Trip full behaviour: new requests route to Waiting List automatically
            // (Business Rules Core User Features Module B). Trips.is_waitlist_allowed
            // exists in the schema but every trip created by the current Create Trip
            // wizard defaults to (and has no UI path to change from) true, so this
            // doesn't yet branch on that flag — revisit once an Organizer can
            // actually disable waitlisting post-publish.
            long waitlisted = joinRequestRepository.countByTripIdAndStatus(tripId, JoinRequestStatus.WAITING_LIST);
            joinRequest.moveToWaitingList((int) waitlisted + 1);
        }
        joinRequest = joinRequestRepository.save(joinRequest);
        tripService.onFirstJoinRequest(tripId);
        return toResponse(joinRequest);
    }

    @Transactional
    public JoinRequestResponse withdraw(UUID actingUserId, UUID joinRequestId) {
        JoinRequest joinRequest = getOrThrow(joinRequestId);
        if (!joinRequest.getApplicantId().equals(actingUserId)) {
            throw new ForbiddenException("You are not the applicant on this request.");
        }
        applyLazyExpiry(joinRequest);
        if (!joinRequest.isOpen()) {
            throw new ConflictException("This request has already been decided.");
        }
        joinRequest.withdraw();
        return toResponse(joinRequestRepository.save(joinRequest));
    }

    /**
     * Returns {@code admitted = false} on the {@link AdmissionResult} when the
     * trip filled up between the Organizer opening the request queue and
     * tapping Accept — the request is already committed as Waiting List by
     * the time this method returns, so the controller (not this method)
     * decides whether/how to surface that as a non-200 response. Throwing
     * from here instead would roll back the very state change this method is
     * responsible for persisting (all exceptions in this codebase are
     * unchecked and trigger a transaction rollback by default).
     */
    @Transactional
    public AcceptOutcome accept(UUID organizerId, UUID joinRequestId) {
        JoinRequest joinRequest = getOrThrow(joinRequestId);
        applyLazyExpiry(joinRequest);
        if (joinRequest.getStatus() == JoinRequestStatus.WAITING_LIST) {
            throw new ConflictException("This request is on the waiting list and will be promoted automatically when a spot opens.");
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new ConflictException("This request has already been decided.");
        }

        TripCapacityInfo trip = tripService.getCapacityInfo(joinRequest.getTripId());
        if (!trip.organizerId().equals(organizerId)) {
            throw new ForbiddenException("Only the organizer can accept join requests for this trip.");
        }
        if (trip.status() == TripStatus.CANCELLED || trip.status() == TripStatus.COMPLETED || trip.status() == TripStatus.ARCHIVED) {
            // Cross-State Validation: "A Join Request cannot be Accepted if the Trip is Cancelled or Completed."
            throw new ConflictException("This trip is no longer accepting members.");
        }

        AdmissionResult admission = membershipService.admitOrWaitlist(joinRequest.getTripId(), joinRequest.getApplicantId(), joinRequest.getId());
        if (admission.admitted()) {
            joinRequest.accept();
            joinRequestRepository.save(joinRequest);
            // Chapter 3 Section 3.5: Chat Locked -> Unlocked, the instant this Join Request reaches Accepted.
            chatService.unlockForUser(joinRequest.getTripId(), joinRequest.getApplicantId());
            return new AcceptOutcome(true, toResponse(joinRequest), admission.tripMember());
        } else {
            long waitlisted = joinRequestRepository.countByTripIdAndStatus(joinRequest.getTripId(), JoinRequestStatus.WAITING_LIST);
            joinRequest.moveToWaitingList((int) waitlisted + 1);
            joinRequestRepository.save(joinRequest);
            return new AcceptOutcome(false, toResponse(joinRequest), null);
        }
    }

    @Transactional
    public JoinRequestResponse reject(UUID organizerId, UUID joinRequestId, RejectJoinRequestRequest request) {
        JoinRequest joinRequest = getOrThrow(joinRequestId);
        applyLazyExpiry(joinRequest);
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new ConflictException("This request has already been decided.");
        }
        TripCapacityInfo trip = tripService.getCapacityInfo(joinRequest.getTripId());
        if (!trip.organizerId().equals(organizerId)) {
            throw new ForbiddenException("Only the organizer can decline join requests for this trip.");
        }
        joinRequest.reject(request.note());
        return toResponse(joinRequestRepository.save(joinRequest));
    }

    public JoinStatusResponse getJoinStatus(UUID viewerId, UUID tripId) {
        JoinRequest joinRequest = joinRequestRepository.findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(viewerId, tripId).orElse(null);
        if (joinRequest == null) {
            return new JoinStatusResponse(null, JoinStatusResponse.NOT_REQUESTED, null, null);
        }
        applyLazyExpiry(joinRequest);
        // An Accepted request's own status never changes on Leave/Remove
        // (membership doesn't write back to join_requests — see
        // MembershipService#isActiveMember's doc). Without this check, a
        // traveller who left would see a stale "You're in!" CTA with no way
        // back to "Request to join". Treated as NOT_REQUESTED rather than
        // inventing a new synthetic status, since re-requesting is exactly
        // what should happen next.
        if (joinRequest.getStatus() == JoinRequestStatus.ACCEPTED && !membershipService.isActiveMember(tripId, viewerId)) {
            return new JoinStatusResponse(null, JoinStatusResponse.NOT_REQUESTED, null, null);
        }
        OffsetDateTime canReapplyAt = null;
        if (joinRequest.getStatus() == JoinRequestStatus.REJECTED) {
            OffsetDateTime cooldownEnds = joinRequest.getDecidedAt().plusDays(REJECT_COOLDOWN_DAYS);
            canReapplyAt = cooldownEnds.isAfter(OffsetDateTime.now()) ? cooldownEnds : null;
        }
        return new JoinStatusResponse(joinRequest.getId(), joinRequest.getStatus().name(), joinRequest.getWaitlistPosition(), canReapplyAt);
    }

    /** Organizer's request queue (API Spec Section 8), oldest-first. */
    public CursorPageResponse<JoinRequestResponse> getOrganizerQueue(UUID organizerId, UUID tripId, JoinRequestStatus statusFilter, String cursor, int limit) {
        TripCapacityInfo trip = tripService.getCapacityInfo(tripId);
        if (!trip.organizerId().equals(organizerId)) {
            throw new ForbiddenException("Only the organizer can view this trip's request queue.");
        }
        int offset = OffsetCursor.decode(cursor);
        PageRequest pageRequest = PageRequest.of(offset / Math.max(limit, 1), limit);
        Page<JoinRequest> page = statusFilter == null
                ? joinRequestRepository.findByTripIdOrderByCreatedAtAsc(tripId, pageRequest)
                : joinRequestRepository.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, statusFilter, pageRequest);
        return toPageResponse(page, offset, limit);
    }

    /** "My pending requests" (API Spec Section 8), newest-first. */
    public CursorPageResponse<JoinRequestResponse> getMyRequests(UUID applicantId, JoinRequestStatus statusFilter, String cursor, int limit) {
        int offset = OffsetCursor.decode(cursor);
        PageRequest pageRequest = PageRequest.of(offset / Math.max(limit, 1), limit);
        Page<JoinRequest> page = statusFilter == null
                ? joinRequestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId, pageRequest)
                : joinRequestRepository.findByApplicantIdAndStatusOrderByCreatedAtDesc(applicantId, statusFilter, pageRequest);
        return toPageResponse(page, offset, limit);
    }

    /**
     * Promotes the FIFO-oldest Waiting List request(s) into Accepted once
     * capacity opens up (Cross-Module Rules: "any Module A capacity increase,
     * or any Module B member departure, is the sole trigger for Waiting List
     * promotion — never a manual Organizer pick-from-waitlist override").
     *
     * <p>Deliberately called from the controller layer right after a
     * successful Leave/Remove (see {@code MembershipController}), not from
     * inside {@code MembershipService} itself — {@code membership} cannot
     * depend on this module without creating the exact circular dependency
     * {@code MembershipService}'s class doc explains avoiding. The one-step
     * lag this introduces (promotion happens in a follow-up transaction
     * rather than atomically with the departure) is a deliberate, bounded
     * trade-off: unlike the Accept-vs-capacity race, there's no way for this
     * gap to double-book a trip, only a brief window before an open spot is
     * offered to the next waitlisted requester.
     */
    @Transactional
    public void promoteWaitlistIfCapacityAvailable(UUID tripId) {
        for (int i = 0; i < 20; i++) { // bounded loop, not expected to iterate more than once per departure at MVP scale
            TripCapacityInfo trip = tripService.lockForCapacityChange(tripId);
            long activeCount = membershipService.countActiveMembers(tripId);
            if (activeCount >= trip.maxGroupSize()) {
                return;
            }
            JoinRequest next = joinRequestRepository.findFirstByTripIdAndStatusOrderByCreatedAtAsc(tripId, JoinRequestStatus.WAITING_LIST).orElse(null);
            if (next == null) {
                return;
            }
            AdmissionResult admission = membershipService.admitOrWaitlist(tripId, next.getApplicantId(), next.getId());
            if (!admission.admitted()) {
                return; // shouldn't happen given the check above, but never loop forever on an inconsistency
            }
            next.promoteFromWaitlist();
            joinRequestRepository.save(next);
            // Chapter 3 Section 3.5: same Chat unlock as a direct Accept — a Waiting List promotion is still "Join Request -> Accepted" from the Chat lifecycle's point of view.
            chatService.unlockForUser(tripId, next.getApplicantId());
        }
    }

    /**
     * Organizer-reliability aggregation for the {@code trust} module (10%
     * weight, organizers only) — {@code tripIds} is the caller's own trips
     * ({@code TripService.listOwnTrips}), fetched by the controller-layer
     * composition that calls this (see {@code TrustService}'s class doc for
     * why {@code trust} reads this module rather than the reverse).
     */
    public OrganizerReliabilityStats getOrganizerReliabilityStats(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return new OrganizerReliabilityStats(0, 0, null);
        }
        List<JoinRequest> decidedOrExpired = joinRequestRepository.findByTripIdInAndStatusIn(
                tripIds, List.of(JoinRequestStatus.ACCEPTED, JoinRequestStatus.REJECTED, JoinRequestStatus.EXPIRED));
        int expired = 0;
        int decided = 0;
        double totalHours = 0;
        for (JoinRequest jr : decidedOrExpired) {
            if (jr.getStatus() == JoinRequestStatus.EXPIRED) {
                expired++;
            } else {
                decided++;
                if (jr.getDecidedAt() != null) {
                    totalHours += Duration.between(jr.getCreatedAt(), jr.getDecidedAt()).toMinutes() / 60.0;
                }
            }
        }
        Double avgHours = decided == 0 ? null : totalHours / decided;
        return new OrganizerReliabilityStats(decided, expired, avgHours);
    }

    // --- internal helpers ---------------------------------------------------

    private void requireIdApproved(UUID userId) {
        if (!enforceIdApproval) {
            return;
        }
        var summary = userService.getSummary(userId);
        if (summary.verificationLevel().ordinal() < VerificationLevel.ID_APPROVED.ordinal()) {
            throw new ForbiddenException("Government ID verification is required to request to join a trip.");
        }
    }

    /**
     * Flips a stale Pending request to Expired on read/write, rather than
     * relying on a scheduled sweep job (Phase 9 concern — see kickoff
     * roadmap's "background jobs" line). This keeps every code path that acts
     * on a request (accept/reject/withdraw/status-check) working against an
     * accurate status without needing a cron job this early.
     */
    private void applyLazyExpiry(JoinRequest joinRequest) {
        if (joinRequest.isExpiredButNotMarked()) {
            joinRequest.expire();
            joinRequestRepository.save(joinRequest);
        }
    }

    private JoinRequest getOrThrow(UUID joinRequestId) {
        return joinRequestRepository.findById(joinRequestId).orElseThrow(() -> ResourceNotFoundException.of("Join request", joinRequestId));
    }

    private CursorPageResponse<JoinRequestResponse> toPageResponse(Page<JoinRequest> page, int offset, int limit) {
        List<JoinRequestResponse> items = page.getContent().stream().map(this::toResponse).toList();
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + limit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    private JoinRequestResponse toResponse(JoinRequest jr) {
        return new JoinRequestResponse(
                jr.getId(), jr.getTripId(), jr.getApplicantId(), jr.getStatus(), jr.getRequestMessage(),
                jr.getOrganizerResponseNote(), jr.getWaitlistPosition(), jr.getDecidedAt(), jr.getExpiresAt(), jr.getCreatedAt());
    }

    /** {@code accept}'s result — see that method's doc for why a lost capacity race is a return value, not an exception. */
    public record AcceptOutcome(boolean admitted, JoinRequestResponse joinRequest, com.gotogether.membership.dto.TripMemberResponse tripMember) {}
}
