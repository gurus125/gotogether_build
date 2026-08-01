package com.gotogether.trip.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * The core entity of the product — one lifecycle for both Community and
 * Verified Partner trips (Chapter 1 Section 13, Chapter 3 Section 3.2).
 *
 * <p>{@code organizerId}/{@code destinationId}/{@code companyId} are plain
 * {@code UUID} fields, deliberately not JPA relationships — {@code trip} is a
 * different module than {@code user}, {@code destination}, and (eventually)
 * {@code company}, and the architecture rule (enforced by {@code
 * ArchitectureTest}) is that modules reference each other's data by id only,
 * never a mapped entity relationship (see {@code UserProfile}'s class doc for
 * the same rule). {@code companyId} is non-null exactly when {@code kind ==
 * VERIFIED_PARTNER} (DB {@code chk_trips_company_id_by_kind}) — see {@link
 * #newVerifiedPartnerDraft} and {@code TripService}'s Phase 7 additions.
 *
 * <p>Enum columns follow the native-Postgres-enum pattern — see {@link
 * NativeEnumJdbcType}'s class doc.
 */
@Entity
@Table(name = "trips")
public class Trip extends AuditableEntity {

    @Column(name = "organizer_id", nullable = false, updatable = false)
    private UUID organizerId;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    /** Always {@code null} at Phase 2 — see class doc. */
    @Column(name = "company_id")
    private UUID companyId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "trip_kind")
    private TripKind kind;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "trip_status")
    private TripStatus status = TripStatus.DRAFT;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "visibility", nullable = false, columnDefinition = "trip_visibility")
    private TripVisibility visibility = TripVisibility.PUBLIC;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "trip_type")
    private String tripType;

    @Column(name = "is_flexible_dates", nullable = false)
    private boolean flexibleDates;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "budget_min")
    private Integer budgetMin;

    @Column(name = "budget_max")
    private Integer budgetMax;

    @Column(name = "fixed_price")
    private Integer fixedPrice;

    @Column(name = "min_group_size", nullable = false)
    private short minGroupSize = 2;

    @Column(name = "max_group_size", nullable = false)
    private short maxGroupSize = 6;

    @Column(name = "is_approval_required", nullable = false)
    private boolean approvalRequired = true;

    @Column(name = "is_waitlist_allowed", nullable = false)
    private boolean waitlistAllowed = true;

    @Column(name = "meeting_point")
    private String meetingPoint;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    protected Trip() {
        // JPA
    }

    /**
     * Creates a Community-trip Draft (Core Features Module A) — the only
     * kind {@link com.gotogether.trip.service.TripService} allows at Phase 2.
     * Everything deferred to "complete later" per the Create Trip Flow design
     * (itinerary, group prefs, packing list, rules, meeting point, etc.) is
     * simply never set here; those live in a future Trip Management update
     * path, not trip creation.
     */
    public static Trip newCommunityDraft(
            UUID organizerId, UUID destinationId, String title, String description,
            boolean flexibleDates, LocalDate startDate, LocalDate endDate,
            Integer budgetMin, Integer budgetMax) {
        Trip trip = new Trip();
        trip.organizerId = organizerId;
        trip.destinationId = destinationId;
        trip.kind = TripKind.COMMUNITY;
        trip.title = title;
        trip.description = description;
        trip.flexibleDates = flexibleDates;
        trip.startDate = startDate;
        trip.endDate = endDate;
        trip.budgetMin = budgetMin;
        trip.budgetMax = budgetMax;
        return trip;
    }

    /**
     * Creates a Verified Partner-trip Draft (Phase 7, Operations Module A:
     * "Creating/editing trips: same field set as Community Trips... plus
     * fixed price, fixed capacity, and cancellation policy text; no
     * auto-generated description shortcut"). {@code organizerId} is the
     * acting company staff user's own id (FK-required — see class doc), but
     * {@code companyId} being non-null is what actually drives every
     * presentation/permission difference (see {@code TripService}'s Phase 7
     * additions) — the "Organizer" a traveller sees is always the Company,
     * per Operations Module A's "Organizer assignment" rule. Cancellation
     * policy text itself lives on {@code TravelCompany}, not per-trip — the
     * Company's one published policy applies to all its trips.
     */
    public static Trip newVerifiedPartnerDraft(
            UUID organizerId, UUID companyId, UUID destinationId, String title, String description,
            boolean flexibleDates, LocalDate startDate, LocalDate endDate, Integer fixedPrice) {
        Trip trip = new Trip();
        trip.organizerId = organizerId;
        trip.companyId = companyId;
        trip.destinationId = destinationId;
        trip.kind = TripKind.VERIFIED_PARTNER;
        trip.title = title;
        trip.description = description;
        trip.flexibleDates = flexibleDates;
        trip.startDate = startDate;
        trip.endDate = endDate;
        trip.fixedPrice = fixedPrice;
        return trip;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public TripKind getKind() {
        return kind;
    }

    public TripStatus getStatus() {
        return status;
    }

    public TripVisibility getVisibility() {
        return visibility;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTripType() {
        return tripType;
    }

    public boolean isFlexibleDates() {
        return flexibleDates;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getBudgetMin() {
        return budgetMin;
    }

    public void setBudgetMin(Integer budgetMin) {
        this.budgetMin = budgetMin;
    }

    public Integer getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(Integer budgetMax) {
        this.budgetMax = budgetMax;
    }

    public Integer getFixedPrice() {
        return fixedPrice;
    }

    public short getMinGroupSize() {
        return minGroupSize;
    }

    /**
     * Manage Trip (post-publish) — {@code TripService#updateTrip} is the only
     * caller; never collected by {@code CreateTripRequest} (Chapter 3's
     * "complete later" fields, see {@link #newCommunityDraft}'s doc). DB
     * {@code chk_trips_min_group_size}/{@code chk_trips_max_group_size} are
     * the backstop; {@code TripService} validates first for a clean error.
     */
    public void setMinGroupSize(short minGroupSize) {
        this.minGroupSize = minGroupSize;
    }

    public short getMaxGroupSize() {
        return maxGroupSize;
    }

    /** See {@link #setMinGroupSize}'s doc — same rule, same caller. */
    public void setMaxGroupSize(short maxGroupSize) {
        this.maxGroupSize = maxGroupSize;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public boolean isWaitlistAllowed() {
        return waitlistAllowed;
    }

    public void setWaitlistAllowed(boolean waitlistAllowed) {
        this.waitlistAllowed = waitlistAllowed;
    }

    public String getMeetingPoint() {
        return meetingPoint;
    }

    public void setMeetingPoint(String meetingPoint) {
        this.meetingPoint = meetingPoint;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public boolean isDraft() {
        return status == TripStatus.DRAFT;
    }

    public boolean isOwnedBy(UUID userId) {
        return organizerId.equals(userId);
    }

    /** Chapter 3 Section 3.2: {@code Draft -> Published} only; validates required fields are present. */
    public void publish() {
        this.status = TripStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
    }

    /**
     * Chapter 3 Section 3.2: any non-terminal state -> {@code Cancelled},
     * mandatory reason (Chapter 2 Section 2.7). Terminal-state re-cancellation
     * is rejected by {@code TripService}, not here, so the error can carry a
     * clearer message with the current status.
     */
    public void cancel(String reason) {
        this.status = TripStatus.CANCELLED;
        this.cancelledAt = OffsetDateTime.now();
        this.cancellationReason = reason;
    }

    public boolean isTerminal() {
        return status == TripStatus.CANCELLED || status == TripStatus.COMPLETED || status == TripStatus.ARCHIVED;
    }

    /**
     * {@code POST /admin/trips/{id}/hide} (Phase 8, Operations Module C:
     * "Moderator hide-only, Admin full incl. force-cancel"). Reuses {@link
     * TripStatus#ARCHIVED} — modeled in the enum since Phase 2 (see that
     * class's own doc) but never previously reached by any code path — rather
     * than adding a new schema column, since hiding pulls a trip out of every
     * discoverable listing without the refund/notification machinery a real
     * {@link #cancel} implies. Deliberately does not record a reason on the
     * entity itself: the moderator's rationale is written to the {@code
     * audit_logs} row instead (Phase 8's {@code admin} module), which is
     * exactly what that table's {@code old_value}/{@code new_value} JSONB
     * columns exist for — see {@code AdminService}'s class doc.
     */
    public void hide() {
        this.status = TripStatus.ARCHIVED;
    }

    // --- Phase 3: capacity-driven transitions (Chapter 3 Section 3.2), all
    // called by TripService only in response to joinrequest/membership events
    // — this module never decides *when* these fire, only *what happens* when
    // told to. ------------------------------------------------------------

    /** {@code Published -> AcceptingRequests}: the trip's first Join Request (of any outcome) has arrived. */
    public void beginAcceptingRequests() {
        this.status = TripStatus.ACCEPTING_REQUESTS;
    }

    /** {@code AcceptingRequests -> Confirmed}: the Organizer-set (or platform-default) minimum viable group size has been reached. */
    public void confirm() {
        this.status = TripStatus.CONFIRMED;
    }

    /** {@code Confirmed -> Full} (or directly from AcceptingRequests if min == max): the max group size has been reached. */
    public void markFull() {
        this.status = TripStatus.FULL;
    }

    /**
     * {@code Full -> AcceptingRequests}: a Member dropped out, freeing a spot.
     * Modeled exactly as the Chapter 3 Section 3.2 diagram draws it — a
     * departure from Full always lands back on AcceptingRequests, even if the
     * remaining count is still at/above the minimum viable size. Chapter 3's
     * own note that "minimum viable size is a recommended UI signal, not a
     * hard gate" is why this doesn't try to re-derive Confirmed instead.
     */
    public void reopenAcceptingRequests() {
        this.status = TripStatus.ACCEPTING_REQUESTS;
    }

    /**
     * {@code InProgress -> Completed}, manual early-complete path (API Spec
     * Section 9: organizer-triggered, only after {@code start_date} — the
     * scheduled at-{@code end_date} auto-complete job is a separate Phase 9
     * concern). Guard (organizer/permission/date checks) lives in {@code
     * TripService}, matching {@link #publish()}/{@link #cancel(String)}'s pattern.
     */
    public void complete() {
        this.status = TripStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    /**
     * {@code (Published|AcceptingRequests|Confirmed|Full) -> InProgress}, the
     * scheduled-at-{@code start_date} job this class's own doc on {@link
     * #complete()} always pointed to as a separate concern — it never got
     * built until now. System-triggered only (no organizer involved, see
     * {@code TripService#systemMarkInProgress}); a trip that never reached at
     * least {@code Published} (i.e. still {@code Draft}) is not eligible —
     * the scheduler's own repository query already excludes Draft, this is a
     * second line of defense at the entity level.
     */
    public void startInProgress() {
        this.status = TripStatus.IN_PROGRESS;
    }
}
