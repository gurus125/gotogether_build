package com.gotogether.trip.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.company.dto.CompanySummary;
import com.gotogether.company.service.CompanyService;
import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.destination.service.DestinationService;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.CancelTripRequest;
import com.gotogether.trip.dto.CreateTripRequest;
import com.gotogether.trip.dto.OrganizerSummary;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.dto.TripDetailsResponse;
import com.gotogether.trip.dto.TripImageResponse;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.dto.UpdateTripRequest;
import com.gotogether.trip.entity.SavedTrip;
import com.gotogether.trip.entity.Trip;
import com.gotogether.trip.entity.TripImage;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.repository.SavedTripRepository;
import com.gotogether.trip.repository.TripImageRepository;
import com.gotogether.trip.repository.TripRepository;
import com.gotogether.trip.repository.TripSpecifications;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The trip module's only entry point for other modules (see {@code
 * UserService}'s doc for the same pattern) — consumes {@code UserService}
 * (verification-level gate), {@code ProfileService} (organizer display info),
 * and {@code DestinationService} (validate/embed destination), never their
 * repositories or entities directly.
 *
 * <p><b>Lifecycle transitions this module implements</b> (Chapter 3 Section
 * 3.2): {@code Draft -> Published} ({@link #publish}) and any non-terminal
 * state {@code -> Cancelled} ({@link #cancel}). The remaining transitions
 * ({@code AcceptingRequests}/{@code Confirmed}/{@code Full} on join-request
 * activity, {@code InProgress}/{@code Completed} on scheduled jobs) belong to
 * {@code joinrequest}/{@code membership} (Phase 3) and a future scheduler
 * (Phase 9) respectively — {@code TripStatus} models every state so the
 * column round-trips correctly, but nothing in this module drives those
 * transitions yet.
 */
@Service
public class TripService {

    /** Chapter 3 Section 13's binding rule: only ID Approved grants trip creation/join permissions. */
    private static final VerificationLevel MIN_VERIFICATION_TO_CREATE = VerificationLevel.ID_APPROVED;

    /**
     * Test-only escape hatch for local MVP testing, where getting a seed
     * account through real ID verification isn't worth the friction. Defaults
     * to {@code true} (the real Chapter 3 Section 13 rule stays enforced) in
     * {@code application.yml}; only {@code application-dev.yml} flips it to
     * {@code false}, so this can never silently ship disabled in a real
     * environment. Not a doc-sanctioned rule change — flagging it here rather
     * than deleting {@link #requireIdApproved} outright.
     */
    @Value("${gotogether.verification.enforce-id-approval:true}")
    private boolean enforceIdApproval;

    /** Statuses a trip must be in to be publicly discoverable (GET /trips, /explore, /trips/recommended) — Draft is never listed to anyone but its own organizer. */
    private static final List<TripStatus> DISCOVERABLE_STATUSES =
            List.of(TripStatus.PUBLISHED, TripStatus.ACCEPTING_REQUESTS, TripStatus.CONFIRMED, TripStatus.FULL);

    /**
     * Statuses eligible for the scheduler's {@code -> InProgress} sweep.
     * Currently identical to {@link #DISCOVERABLE_STATUSES}, but kept as its
     * own constant rather than reused — "can travellers currently find this
     * trip" and "can this trip start" are different questions that happen to
     * share an answer today; coupling them risks a subtle bug the day either
     * rule changes independently.
     */
    private static final List<TripStatus> STATUSES_ELIGIBLE_FOR_IN_PROGRESS =
            List.of(TripStatus.PUBLISHED, TripStatus.ACCEPTING_REQUESTS, TripStatus.CONFIRMED, TripStatus.FULL);

    private final TripRepository tripRepository;
    private final TripImageRepository tripImageRepository;
    private final SavedTripRepository savedTripRepository;
    private final UserService userService;
    private final ProfileService profileService;
    private final DestinationService destinationService;
    private final CompanyService companyService;

    public TripService(
            TripRepository tripRepository, TripImageRepository tripImageRepository,
            SavedTripRepository savedTripRepository, UserService userService,
            ProfileService profileService, DestinationService destinationService,
            CompanyService companyService) {
        this.tripRepository = tripRepository;
        this.tripImageRepository = tripImageRepository;
        this.savedTripRepository = savedTripRepository;
        this.userService = userService;
        this.profileService = profileService;
        this.destinationService = destinationService;
        this.companyService = companyService;
    }

    /**
     * {@code companyId} non-null (Phase 7) routes to the Verified Partner
     * path: gated on the caller being an active staff member of that company
     * ({@link CompanyService#assertActiveMember}, Fix #4/DB Review) and the
     * company actually being {@code VERIFIED} ({@link
     * CompanyService#assertVerified}) — see that class's doc for why the
     * latter can't yet be exercised in a real environment without Phase 8.
     * {@code null} (the common case) is the pre-existing Community path,
     * unchanged.
     */
    @Transactional
    public TripResponse createDraft(UUID organizerId, CreateTripRequest request) {
        DestinationSummary destination = destinationService.getSummary(request.destinationId());

        Trip trip;
        if (request.companyId() != null) {
            companyService.assertActiveMember(request.companyId(), organizerId);
            companyService.assertVerified(request.companyId());
            validateDates(request.startDate(), request.endDate());
            trip = Trip.newVerifiedPartnerDraft(
                    organizerId, request.companyId(), destination.id(), request.title(), request.description(),
                    request.isFlexibleDates(), request.startDate(), request.endDate(), request.fixedPrice());
        } else {
            requireIdApproved(organizerId);
            validateDates(request.startDate(), request.endDate());
            validateBudget(request.budgetMin(), request.budgetMax());
            trip = Trip.newCommunityDraft(
                    organizerId, destination.id(), request.title(), request.description(),
                    request.isFlexibleDates(), request.startDate(), request.endDate(),
                    request.budgetMin(), request.budgetMax());
        }
        trip = tripRepository.save(trip);
        return toResponse(trip, destination);
    }

    /**
     * {@code currentActiveMembers} is passed in by {@code TripController}
     * (from {@code MembershipService#countActiveMembers}) rather than looked
     * up here — {@code trip} never depends on {@code membership} directly
     * (see this class's own doc on module dependency direction), so the one
     * cross-module fact this validation needs is supplied by the caller as a
     * plain value instead. Only actually consulted when {@code
     * request.maxGroupSize()} is present.
     */
    @Transactional
    public TripResponse updateTrip(UUID userId, UUID tripId, UpdateTripRequest request, long currentActiveMembers) {
        Trip trip = getOwnedTripOrThrow(userId, tripId);
        requireEditable(trip);

        UUID destinationId = trip.getDestinationId();
        if (request.destinationId() != null) {
            destinationId = destinationService.getSummary(request.destinationId()).id();
            trip.setDestinationId(destinationId);
        }
        if (request.title() != null) trip.setTitle(request.title());
        if (request.description() != null) trip.setDescription(request.description());
        if (request.startDate() != null) trip.setStartDate(request.startDate());
        if (request.endDate() != null) trip.setEndDate(request.endDate());
        if (request.budgetMin() != null) trip.setBudgetMin(request.budgetMin());
        if (request.budgetMax() != null) trip.setBudgetMax(request.budgetMax());
        if (request.meetingPoint() != null) trip.setMeetingPoint(request.meetingPoint());
        if (request.isApprovalRequired() != null) trip.setApprovalRequired(request.isApprovalRequired());
        if (request.isWaitlistAllowed() != null) trip.setWaitlistAllowed(request.isWaitlistAllowed());

        if (request.maxGroupSize() != null && request.maxGroupSize() < currentActiveMembers) {
            throw new UnprocessableEntityException(
                    "max_group_size can't be set below the " + currentActiveMembers + " member(s) already on this trip.");
        }
        if (request.minGroupSize() != null) trip.setMinGroupSize(request.minGroupSize().shortValue());
        if (request.maxGroupSize() != null) trip.setMaxGroupSize(request.maxGroupSize().shortValue());

        validateDates(trip.getStartDate(), trip.getEndDate());
        validateBudget(trip.getBudgetMin(), trip.getBudgetMax());
        validateGroupSize(trip.getMinGroupSize(), trip.getMaxGroupSize());

        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(destinationId));
    }

    @Transactional
    public TripResponse publish(UUID userId, UUID tripId) {
        Trip trip = getOwnedTripOrThrow(userId, tripId);
        if (!trip.isDraft()) {
            throw new ConflictException("Trip is already published.");
        }
        if (trip.getDescription() == null || trip.getDescription().isBlank()) {
            throw new UnprocessableEntityException("A description is required before publishing.");
        }
        trip.publish();
        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(trip.getDestinationId()));
    }

    @Transactional
    public TripResponse cancel(UUID userId, AccountRole role, UUID tripId, CancelTripRequest request) {
        Trip trip = getTripOrThrow(tripId);
        boolean isModerator = role == AccountRole.MODERATOR || role == AccountRole.ADMIN;
        if (!trip.isOwnedBy(userId) && !isModerator) {
            throw new ForbiddenException("Only the organizer or a moderator can cancel this trip.");
        }
        if (trip.isTerminal()) {
            throw new ConflictException("Trip is already in a terminal state.");
        }
        trip.cancel(request.reason());
        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(trip.getDestinationId()));
    }

    /**
     * {@code POST /admin/trips/{id}/hide} (Phase 8, API Spec Section 16) —
     * called from {@code admin.service.AdminService}, which owns the
     * moderator|admin role gate and the {@code audit_logs} write (see that
     * class's doc). Distinct from {@link #cancel}: hiding removes public
     * discoverability only, with none of a real cancellation's refund/
     * notification side effects.
     */
    @Transactional
    public TripResponse adminHide(UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        if (trip.isTerminal()) {
            throw new ConflictException("Trip is already in a terminal state.");
        }
        trip.hide();
        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(trip.getDestinationId()));
    }

    /** Hard-delete, Draft only (Core Features Module A) — published trips use cancel, never delete. */
    @Transactional
    public void deleteTrip(UUID userId, UUID tripId) {
        Trip trip = getOwnedTripOrThrow(userId, tripId);
        if (!trip.isDraft()) {
            throw new ConflictException("Only a Draft trip can be deleted — cancel a published trip instead.");
        }
        tripRepository.delete(trip);
    }

    /**
     * Organizer-only check exposed so {@code TripController} can reject an
     * unauthorized photo-upload request at the presigned-URL step — before
     * any call to {@code StorageService}/S3 — rather than only catching it
     * later at {@link #addImage}. Same rule as every other organizer-only
     * mutation in this class ({@link #updateTrip}, {@link #deleteTrip}).
     */
    public void assertOrganizer(UUID userId, UUID tripId) {
        getOwnedTripOrThrow(userId, tripId);
    }

    /**
     * Trip gallery management (photo upload, Manage Trip Photos) — organizer
     * only, same ownership check as {@link #updateTrip}. {@code
     * display_order} is always appended (next available index); reordering
     * isn't supported yet, only add/delete and toggling {@code is_primary}.
     * Setting a new primary unsets whichever image previously held it — the
     * DB's {@code ux_trip_images_one_primary_per_trip} partial unique index
     * is the actual backstop (see {@link TripImage}'s class doc), this is
     * just doing the unset half of that swap explicitly.
     */
    @Transactional
    public TripImageResponse addImage(UUID userId, UUID tripId, String imageUrl, boolean primary) {
        Trip trip = getOwnedTripOrThrow(userId, tripId);
        List<TripImage> existing = tripImageRepository.findByTripIdOrderByDisplayOrderAsc(trip.getId());
        if (primary) {
            existing.stream().filter(TripImage::isPrimary).forEach(img -> {
                img.setPrimary(false);
                tripImageRepository.save(img);
            });
        }
        TripImage image = TripImage.of(trip.getId(), imageUrl, (short) existing.size(), primary);
        image = tripImageRepository.save(image);
        return new TripImageResponse(image.getId(), image.getImageUrl(), image.getDisplayOrder(), image.isPrimary());
    }

    @Transactional
    public void deleteImage(UUID userId, UUID tripId, UUID imageId) {
        Trip trip = getOwnedTripOrThrow(userId, tripId);
        TripImage image = tripImageRepository.findById(imageId)
                .filter(img -> img.getTripId().equals(trip.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("TripImage", imageId));
        tripImageRepository.delete(image);
    }

    public TripDetailsResponse getTripDetails(UUID viewerId, UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        // A Draft is only visible to its own organizer — never distinguishes
        // "doesn't exist" from "not visible to you" (API Spec Section 21).
        if (trip.isDraft() && !trip.isOwnedBy(viewerId)) {
            throw ResourceNotFoundException.of("Trip", tripId);
        }

        DestinationSummary destination = destinationService.getSummary(trip.getDestinationId());
        OrganizerSummary organizer = buildOrganizerSummary(trip);

        // membersPreview/compatibilityScore/joinStatus are intentionally absent
        // at Phase 2 — see TripDetailsResponse's class doc for exactly why.
        return new TripDetailsResponse(toResponse(trip, destination), organizer, List.of(), null, null);
    }

    public CursorPageResponse<TripSummary> listTrips(
            UUID destinationId, TripKind kind, TripStatus status, String cursor, int limit) {
        Specification<Trip> spec = Specification.allOf(
                TripSpecifications.destinationId(destinationId),
                TripSpecifications.kind(kind),
                status != null ? TripSpecifications.status(status) : TripSpecifications.statusIn(DISCOVERABLE_STATUSES),
                TripSpecifications.companyIdNotIn(companyService.getDiscoveryExcludedCompanyIds()));
        return page(spec, Sort.by(Sort.Direction.DESC, "createdAt"), cursor, limit);
    }

    /**
     * "Trips For You" (Home Screen, API Spec Section 6). The documented
     * ranking is Business Rules Trust & Discovery Module D's "Best Match"
     * formula, which is not yet defined (flagged during the Phase 2 docs
     * review — Chapter 4 dependency). This is a placeholder: newest-published
     * first, excluding the caller's own trips, restricted to discoverable
     * statuses. Revisit once Chapter 4 defines the real formula.
     */
    public CursorPageResponse<TripSummary> recommended(UUID viewerId, String cursor, int limit) {
        Specification<Trip> spec = Specification.allOf(
                TripSpecifications.statusIn(DISCOVERABLE_STATUSES),
                TripSpecifications.organizerIdNot(viewerId),
                TripSpecifications.companyIdNotIn(companyService.getDiscoveryExcludedCompanyIds()));
        return page(spec, Sort.by(Sort.Direction.DESC, "publishedAt"), cursor, limit);
    }

    /**
     * {@code GET /explore} (API Specification Section 7). {@code duration}
     * (trip length in days) is filtered in-memory post-query rather than as a
     * {@link Specification} — see {@code TripSpecifications}' comment for why.
     * {@code sort} values {@code best_match}/{@code highest_trust}/{@code
     * most_members} all depend on data this module doesn't have yet
     * (Chapter 4's compatibility formula, Phase 5 trust scores, Phase 3
     * membership counts) and fall back to {@code newest} rather than erroring,
     * so the Explore screen still works end-to-end at Phase 2.
     *
     * <p>Also excludes any Verified Partner Trip belonging to a suspended or
     * removed Company (fixed post-Phase-9 — see {@code CompanyService
     * #getDiscoveryExcludedCompanyIds}'s doc for the Operations Module A rule
     * this was previously missing), same as {@link #recommended} and {@link
     * #listTrips}.
     */
    public CursorPageResponse<TripSummary> explore(
            UUID destinationId, Integer budgetMin, Integer budgetMax, LocalDate dateFrom, LocalDate dateTo,
            Integer durationMinDays, Integer durationMaxDays, String tripType, TripKind kind,
            boolean verifiedOnly, String sort, String cursor, int limit) {
        Specification<Trip> spec = Specification.allOf(
                TripSpecifications.statusIn(DISCOVERABLE_STATUSES),
                TripSpecifications.destinationId(destinationId),
                TripSpecifications.budgetMinAtLeast(budgetMin),
                TripSpecifications.budgetMaxAtMost(budgetMax),
                TripSpecifications.dateFrom(dateFrom),
                TripSpecifications.dateTo(dateTo),
                TripSpecifications.tripType(tripType),
                TripSpecifications.kind(kind),
                TripSpecifications.verifiedOnly(verifiedOnly),
                TripSpecifications.companyIdNotIn(companyService.getDiscoveryExcludedCompanyIds()));

        Sort jpaSort = switch (sort == null ? "best_match" : sort) {
            case "leaving_soon" -> Sort.by(Sort.Direction.ASC, "startDate");
            case "lowest_budget" -> Sort.by(Sort.Direction.ASC, "budgetMin");
            default -> Sort.by(Sort.Direction.DESC, "createdAt"); // newest, and the best_match/highest_trust/most_members fallback
        };

        if (durationMinDays == null && durationMaxDays == null) {
            return page(spec, jpaSort, cursor, limit);
        }

        // Duration filtering happens in-memory (see class doc) — fetch a wider
        // page from the DB, filter, then re-apply cursor semantics on the
        // filtered set. Acceptable at MVP dataset sizes (Chapter 1 Section 14).
        List<Trip> candidates = tripRepository.findAll(spec, jpaSort.and(Sort.by(Sort.Direction.ASC, "id")));
        List<Trip> filtered = candidates.stream()
                .filter(t -> {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(t.getStartDate(), t.getEndDate()) + 1;
                    return (durationMinDays == null || days >= durationMinDays) && (durationMaxDays == null || days <= durationMaxDays);
                })
                .toList();
        return sliceAndMap(filtered, cursor, limit);
    }

    // --- Phase 3 additions: capacity-driven lifecycle, called only by
    // joinrequest/membership (never triggered internally by this module) ---

    /** Read-only, unlocked snapshot — used for permission/joinability checks that don't need to be inside the capacity-mutating transaction. */
    public TripCapacityInfo getCapacityInfo(UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        return new TripCapacityInfo(trip.getId(), trip.getOrganizerId(), trip.getKind(), trip.getStatus(), trip.getMinGroupSize(), trip.getMaxGroupSize());
    }

    /** Single-trip card representation — used by {@code MembershipService} to build My Trips' Upcoming/Past tabs from a caller's {@code trip_members} rows. */
    public TripSummary getSummary(UUID tripId) {
        return toSummary(getTripOrThrow(tripId));
    }

    /**
     * Locks the trip row (see {@code TripRepository#findByIdForUpdate}) for
     * the remainder of the caller's transaction. Must only be called from
     * within an existing {@code @Transactional} method in {@code
     * joinrequest}/{@code membership} — Spring's default {@code REQUIRED}
     * propagation means this joins that transaction rather than starting a
     * new one, so the lock's scope naturally covers the whole
     * check-then-insert operation the caller performs afterwards.
     */
    @Transactional
    public TripCapacityInfo lockForCapacityChange(UUID tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId).orElseThrow(() -> ResourceNotFoundException.of("Trip", tripId));
        return new TripCapacityInfo(trip.getId(), trip.getOrganizerId(), trip.getKind(), trip.getStatus(), trip.getMinGroupSize(), trip.getMaxGroupSize());
    }

    /** {@code Published -> AcceptingRequests} on the trip's first Join Request. No-op if the trip isn't currently Published (idempotent, safe to call unconditionally on every Join Request creation). */
    @Transactional
    public void onFirstJoinRequest(UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        if (trip.getStatus() == TripStatus.PUBLISHED) {
            trip.beginAcceptingRequests();
            tripRepository.save(trip);
        }
    }

    /**
     * Recomputes the capacity-threshold transitions after a {@code
     * trip_members} change (an Accept, a Leave, or a Removal) — {@code
     * -> Full} at max, {@code Full -> AcceptingRequests} on a drop-out, {@code
     * -> Confirmed} at the minimum viable size. A no-op once the trip has
     * departed ({@code InProgress}) or reached a terminal state — capacity no
     * longer matters once boarding has effectively closed.
     */
    @Transactional
    public void updateCapacityStatus(UUID tripId, long acceptedMemberCount) {
        Trip trip = getTripOrThrow(tripId);
        if (trip.isTerminal() || trip.getStatus() == TripStatus.IN_PROGRESS) {
            return;
        }
        if (acceptedMemberCount >= trip.getMaxGroupSize()) {
            trip.markFull();
        } else {
            if (trip.getStatus() == TripStatus.FULL) {
                trip.reopenAcceptingRequests();
            }
            if (trip.getStatus() == TripStatus.ACCEPTING_REQUESTS && acceptedMemberCount >= trip.getMinGroupSize()) {
                trip.confirm();
            }
        }
        tripRepository.save(trip);
    }

    /**
     * Manual early-complete (API Spec Section 9: {@code POST /trips/{id}/complete}) —
     * organizer-only, only permitted once the trip is In Progress and only
     * after {@code start_date}. {@link #systemMarkCompleted} is the scheduled
     * at-{@code end_date} counterpart (Chapter 3 Section 3.2's normal path) —
     * for a long time genuinely unimplemented (this doc used to say so), now
     * built as {@code TripLifecycleScheduler}.
     */
    @Transactional
    public TripResponse markCompleted(UUID userId, UUID tripId) {
        Trip trip = getOwnedTripOrThrow(userId, tripId);
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException("Only a trip that is In Progress can be marked complete.");
        }
        if (LocalDate.now().isBefore(trip.getStartDate())) {
            throw new UnprocessableEntityException("A trip cannot be completed before its start date.");
        }
        trip.complete();
        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(trip.getDestinationId()));
    }

    /**
     * System-triggered counterpart of {@link #markCompleted} — no organizer
     * to check ownership against, called only from {@code
     * TripLifecycleScheduler} for a trip its own query already confirmed is
     * {@code IN_PROGRESS} with a passed {@code end_date}. Still re-validates
     * the status defensively (a trip could theoretically have been cancelled
     * between the query and this call within the same scheduler run) rather
     * than trusting the caller's filter blindly.
     */
    @Transactional
    public TripResponse systemMarkCompleted(UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException("Only a trip that is In Progress can be marked complete.");
        }
        trip.complete();
        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(trip.getDestinationId()));
    }

    /**
     * System-triggered {@code -> InProgress} transition — see {@link
     * Trip#startInProgress()}'s doc. Called only from {@code
     * TripLifecycleScheduler} for a trip its own query already confirmed is
     * eligible ({@link #STATUSES_ELIGIBLE_FOR_IN_PROGRESS}) with a
     * start_date that has arrived; re-validates defensively for the same
     * reason as {@link #systemMarkCompleted}.
     */
    @Transactional
    public TripResponse systemMarkInProgress(UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        if (!STATUSES_ELIGIBLE_FOR_IN_PROGRESS.contains(trip.getStatus())) {
            throw new ConflictException("Trip status " + trip.getStatus() + " cannot transition to In Progress.");
        }
        trip.startInProgress();
        trip = tripRepository.save(trip);
        return toResponse(trip, destinationService.getSummary(trip.getDestinationId()));
    }

    /** {@code TripLifecycleScheduler}'s query for trips whose {@code start_date} has arrived and are still in a pre-InProgress state. */
    public List<UUID> findTripIdsReadyToStart() {
        return tripRepository.findByStatusInAndStartDateLessThanEqual(STATUSES_ELIGIBLE_FOR_IN_PROGRESS, LocalDate.now())
                .stream().map(Trip::getId).toList();
    }

    /** {@code TripLifecycleScheduler}'s query for InProgress trips whose {@code end_date} has fully passed. */
    public List<UUID> findTripIdsReadyToComplete() {
        return tripRepository.findByStatusAndEndDateLessThan(TripStatus.IN_PROGRESS, LocalDate.now())
                .stream().map(Trip::getId).toList();
    }

    /** "Created" tab of My Trips (API Spec Section 6) — every trip the caller organizes, any status, newest first. */
    public List<TripSummary> listOwnTrips(UUID organizerId) {
        return tripRepository.findByOrganizerIdOrderByCreatedAtDesc(organizerId).stream().map(this::toSummary).toList();
    }

    // --- Phase 7 cross-module entry points (called by CompanyController — see its class doc) ---

    /**
     * {@code GET /companies/me/trips} — a Company's own Verified Partner
     * Trips, "same shape as regular Trip endpoints" (API Spec Section 14).
     * {@code status} is a {@code String} rather than {@link TripStatus}
     * directly — {@code CompanyController} lives in a different module, and
     * accepting the enum there would be exactly the cross-module entity
     * access {@code ArchitectureTest} forbids (same reason {@code
     * notification.service.NotificationService#create} takes a {@code
     * String type} instead of its own entity's enum).
     */
    public CursorPageResponse<TripSummary> listCompanyTrips(UUID companyId, String status, String cursor, int limit) {
        TripStatus parsedStatus = status == null ? null : TripStatus.valueOf(status.toUpperCase());
        Specification<Trip> spec = Specification.allOf(
                TripSpecifications.companyId(companyId),
                parsedStatus != null ? TripSpecifications.status(parsedStatus) : null);
        return page(spec, Sort.by(Sort.Direction.DESC, "createdAt"), cursor, limit);
    }

    /** Every trip id this Company has ever run — feeds {@code ReviewService.averageOverallRatingForTrips} for the public Company Profile's aggregate rating. */
    public List<UUID> listCompanyTripIds(UUID companyId) {
        return tripRepository.findByCompanyId(companyId).stream().map(Trip::getId).toList();
    }

    /** {@code trips_completed_count} on the public Company Profile (Operations Module A). */
    public int countCompletedTripsForCompany(UUID companyId) {
        return (int) tripRepository.countByCompanyIdAndStatus(companyId, TripStatus.COMPLETED);
    }

    /** "Saved" tab of My Trips — wraps the {@code saved_trips} bookmark join added in Phase 2. */
    public List<TripSummary> listSavedTripsFor(UUID userId) {
        return savedTripRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(saved -> toSummary(getTripOrThrow(saved.getTripId())))
                .toList();
    }

    @Transactional
    public void saveTrip(UUID userId, UUID tripId) {
        getTripOrThrow(tripId);
        if (savedTripRepository.existsByUserIdAndTripId(userId, tripId)) {
            throw new ConflictException("Trip is already saved.");
        }
        savedTripRepository.save(SavedTrip.of(userId, tripId));
    }

    @Transactional
    public void unsaveTrip(UUID userId, UUID tripId) {
        savedTripRepository.findByUserIdAndTripId(userId, tripId)
                .ifPresent(savedTripRepository::delete);
    }

    // --- internal helpers ---------------------------------------------------

    private void requireIdApproved(UUID userId) {
        if (!enforceIdApproval) {
            return;
        }
        var summary = userService.getSummary(userId);
        if (summary.verificationLevel().ordinal() < MIN_VERIFICATION_TO_CREATE.ordinal()) {
            throw new ForbiddenException("Government ID verification is required to create a trip.");
        }
    }

    private void requireEditable(Trip trip) {
        if (trip.getStatus() == TripStatus.IN_PROGRESS || trip.isTerminal()) {
            throw new UnprocessableEntityException("This trip can no longer be edited (Chapter 3: editing locks once a trip is in progress or finished).");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        if (startDate.isBefore(tomorrow)) {
            throw new UnprocessableEntityException("start_date must be at least tomorrow.");
        }
        if (endDate.isBefore(startDate)) {
            throw new UnprocessableEntityException("end_date must be on or after start_date.");
        }
    }

    private void validateBudget(Integer budgetMin, Integer budgetMax) {
        if (budgetMin != null && budgetMax != null && budgetMax < budgetMin) {
            throw new UnprocessableEntityException("budget_max must be greater than or equal to budget_min.");
        }
    }

    /** Mirrors DB {@code chk_trips_min_group_size}/{@code chk_trips_max_group_size} — checked here first for a clean error instead of a raw constraint violation. */
    private void validateGroupSize(short minGroupSize, short maxGroupSize) {
        if (maxGroupSize < minGroupSize) {
            throw new UnprocessableEntityException("max_group_size must be greater than or equal to min_group_size.");
        }
    }

    private Trip getTripOrThrow(UUID tripId) {
        return tripRepository.findById(tripId).orElseThrow(() -> ResourceNotFoundException.of("Trip", tripId));
    }

    private Trip getOwnedTripOrThrow(UUID userId, UUID tripId) {
        Trip trip = getTripOrThrow(tripId);
        if (!trip.isOwnedBy(userId)) {
            throw new ForbiddenException("You are not the organizer of this trip.");
        }
        return trip;
    }

    private CursorPageResponse<TripSummary> page(Specification<Trip> spec, Sort sort, String cursor, int limit) {
        int offset = OffsetCursor.decode(cursor);
        var pageRequest = PageRequest.of(offset / Math.max(limit, 1), limit, sort.and(Sort.by(Sort.Direction.ASC, "id")));
        var result = tripRepository.findAll(spec, pageRequest);
        List<TripSummary> items = result.getContent().stream().map(this::toSummary).toList();
        String nextCursor = result.hasNext() ? OffsetCursor.encode(offset + limit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    private CursorPageResponse<TripSummary> sliceAndMap(List<Trip> all, String cursor, int limit) {
        int offset = OffsetCursor.decode(cursor);
        List<Trip> slice = all.stream().skip(offset).limit(limit).toList();
        List<TripSummary> items = slice.stream().map(this::toSummary).toList();
        String nextCursor = offset + limit < all.size() ? OffsetCursor.encode(offset + limit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    private TripResponse toResponse(Trip t, DestinationSummary destination) {
        List<TripImageResponse> images = tripImageRepository.findByTripIdOrderByDisplayOrderAsc(t.getId()).stream()
                .map(img -> new TripImageResponse(img.getId(), img.getImageUrl(), img.getDisplayOrder(), img.isPrimary()))
                .toList();
        return new TripResponse(
                t.getId(), t.getOrganizerId(), t.getCompanyId(), destination, t.getKind(), t.getStatus(), t.getVisibility(),
                t.getTitle(), t.getDescription(), t.getTripType(), t.isFlexibleDates(), t.getStartDate(), t.getEndDate(),
                t.getBudgetMin(), t.getBudgetMax(), t.getFixedPrice(), t.getMinGroupSize(), t.getMaxGroupSize(),
                t.isApprovalRequired(), t.isWaitlistAllowed(), t.getMeetingPoint(), t.getPublishedAt(),
                t.getCancelledAt(), t.getCancellationReason(), t.getCompletedAt(), images, t.getCreatedAt(), t.getUpdatedAt());
    }

    /**
     * Verified Partner Trips show the Company's own branding as "the
     * Organizer," never the acting staff member's personal profile
     * (Operations Module A: "a traveller interacts with 'Summit Travel Co.,'
     * not with a named employee") — {@code organizerId} on the DTOs still
     * carries the real staff user id (needed for ownership/permission
     * checks), but {@code organizerDisplayName}/{@code organizerPhotoUrl}/
     * {@code organizerVerified} are sourced from {@link CompanySummary}
     * instead of {@code ProfileService}/{@code UserService} whenever {@code
     * companyId} is set.
     */
    private OrganizerSummary buildOrganizerSummary(Trip t) {
        if (t.getCompanyId() != null) {
            CompanySummary company = companyService.getSummary(t.getCompanyId());
            return new OrganizerSummary(t.getOrganizerId(), company.displayName(), company.logoUrl(), company.verified());
        }
        ProfilePublicSummary profile = profileService.getPublicSummary(t.getOrganizerId());
        var userSummary = userService.getSummary(t.getOrganizerId());
        return new OrganizerSummary(
                profile.userId(), profile.displayName(), profile.photoUrl(),
                userSummary.verificationLevel() == VerificationLevel.ID_APPROVED);
    }

    private TripSummary toSummary(Trip t) {
        DestinationSummary destination = destinationService.getSummary(t.getDestinationId());
        OrganizerSummary organizer = buildOrganizerSummary(t);
        String coverImageUrl = tripImageRepository.findByTripIdAndPrimaryTrue(t.getId()).stream()
                .findFirst().map(TripImage::getImageUrl)
                .orElseGet(() -> tripImageRepository.findByTripIdOrderByDisplayOrderAsc(t.getId()).stream()
                        .findFirst().map(TripImage::getImageUrl).orElse(null));

        return new TripSummary(
                t.getId(), t.getTitle(), t.getKind(), t.getStatus(), destination, t.getStartDate(), t.getEndDate(),
                t.getBudgetMin(), t.getBudgetMax(), t.getFixedPrice(), t.getMaxGroupSize(), 0, coverImageUrl,
                organizer.id(), organizer.displayName(), organizer.photoUrl(), organizer.idVerified(), t.getCompanyId());
    }
}
