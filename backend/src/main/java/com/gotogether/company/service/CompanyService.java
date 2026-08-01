package com.gotogether.company.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.company.dto.ApplyCompanyRequest;
import com.gotogether.company.dto.CompanyProfileResponse;
import com.gotogether.company.dto.CompanyResponse;
import com.gotogether.company.dto.CompanySummary;
import com.gotogether.company.dto.CompanyUserResponse;
import com.gotogether.company.dto.CompanyVerificationQueueEntry;
import com.gotogether.company.dto.CompanyVerificationStatusResponse;
import com.gotogether.company.dto.InviteStaffRequest;
import com.gotogether.company.entity.CompanyStatus;
import com.gotogether.company.entity.CompanyUser;
import com.gotogether.company.entity.CompanyUserRole;
import com.gotogether.company.entity.CompanyVerification;
import com.gotogether.company.entity.CompanyVerificationStatus;
import com.gotogether.company.entity.TravelCompany;
import com.gotogether.company.repository.CompanyUserRepository;
import com.gotogether.company.repository.CompanyVerificationRepository;
import com.gotogether.company.repository.TravelCompanyRepository;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The company module's only entry point for other modules (same pattern as
 * every other module's {@code Service} — see {@code UserService}'s doc).
 * Consumes {@code UserService} (the applicant's own ID-verification gate),
 * never its repository or entity directly.
 *
 * <p><b>Reaching {@link com.gotogether.company.entity.CompanyStatus#VERIFIED}
 * requires a real Moderator/Admin decision</b> (Operations Module A: "Manual
 * Moderator/Admin review... never automated at MVP scale"), which is Phase
 * 8's {@code admin} module and doesn't exist yet. Rather than fabricating an
 * auto-approve endpoint that would contradict that explicit "never automated"
 * business rule, this mirrors the exact dev-only escape hatch {@code
 * TripService.enforceIdApproval} already uses for the structurally identical
 * problem on the individual-ID-approval side: {@link #enforceCompanyVerification}
 * defaults {@code true} (the real rule stays enforced) and only {@code
 * application-dev.yml} flips it to {@code false}, so a local test company can
 * still create Verified Partner Trips without a real Moderator existing yet.
 * This means, in this pass, a company can apply / manage staff / read its own
 * status end-to-end, but genuinely cannot reach {@code verified} through the
 * API alone in a real environment — a real, flagged gap, not an oversight.
 */
@Service
public class CompanyService {

    @Value("${gotogether.verification.enforce-company-verification:true}")
    private boolean enforceCompanyVerification;

    private static final VerificationLevel MIN_VERIFICATION_TO_APPLY = VerificationLevel.ID_APPROVED;

    /** Operations Module A's Company Suspension rule: "immediately excluded from Explore/Home ranking... without deleting the listings" — see {@link #getDiscoveryExcludedCompanyIds}. */
    private static final List<CompanyStatus> DISCOVERY_EXCLUDED_STATUSES = List.of(CompanyStatus.SUSPENDED, CompanyStatus.REMOVED);

    private final TravelCompanyRepository travelCompanyRepository;
    private final CompanyUserRepository companyUserRepository;
    private final CompanyVerificationRepository companyVerificationRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompanyService(
            TravelCompanyRepository travelCompanyRepository, CompanyUserRepository companyUserRepository,
            CompanyVerificationRepository companyVerificationRepository, UserService userService) {
        this.travelCompanyRepository = travelCompanyRepository;
        this.companyUserRepository = companyUserRepository;
        this.companyVerificationRepository = companyVerificationRepository;
        this.userService = userService;
    }

    /** {@code POST /companies/apply} — the applicant becomes the founding {@link CompanyUserRole#OWNER}, atomically. */
    @Transactional
    public CompanyResponse apply(UUID userId, ApplyCompanyRequest request) {
        var applicant = userService.getSummary(userId);
        if (applicant.verificationLevel().ordinal() < MIN_VERIFICATION_TO_APPLY.ordinal()) {
            throw new ForbiddenException("Government ID verification is required before applying as a Travel Company.");
        }
        if (travelCompanyRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new ConflictException("A company with this registration number is already registered.");
        }

        TravelCompany company = TravelCompany.apply(
                request.displayName(), request.legalName(), request.registrationNumber(), request.gstNumber(),
                request.supportEmail(), request.supportPhone(), request.cancellationPolicy());
        company = travelCompanyRepository.save(company);

        companyUserRepository.save(CompanyUser.owner(company.getId(), userId));
        companyVerificationRepository.save(CompanyVerification.initial(company.getId(), toJson(request.documents())));

        return toResponse(company);
    }

    /** {@code GET /companies/{id}} — the public Company Profile. */
    @Transactional
    public CompanyProfileResponse getPublicProfile(UUID companyId, Double aggregateRating, int tripsCompletedCount) {
        TravelCompany company = getCompanyOrThrow(companyId);
        return new CompanyProfileResponse(
                company.getId(), company.getDisplayName(), company.getLogoUrl(), company.getDescription(),
                company.getWebsiteUrl(), company.getSupportEmail(), company.getSupportPhone(),
                company.getCancellationPolicy(), company.getStatus().name(), aggregateRating, tripsCompletedCount);
    }

    /** {@code GET /companies/me/verification-status}. */
    @Transactional
    public CompanyVerificationStatusResponse getMyVerificationStatus(UUID userId) {
        UUID companyId = getMyCompanyId(userId);
        CompanyVerification latest = companyVerificationRepository.findFirstByCompanyIdOrderByCreatedAtDesc(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company verification", companyId));
        return new CompanyVerificationStatusResponse(latest.getStatus().name(), latest.getDecisionNotes());
    }

    /** {@code GET /companies/me/staff} — Owner only. */
    @Transactional
    public List<CompanyUserResponse> listStaff(UUID actingUserId) {
        CompanyUser acting = getActiveMembershipOrThrow(actingUserId);
        requireOwner(acting);
        return companyUserRepository.findByCompanyIdAndStatus(acting.getCompanyId(), "active").stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * {@code POST /companies/me/staff} — Owner only. Requesting {@code owner}
     * always fails with {@code MULTI_ADMIN_NOT_ENABLED} (Operations Module
     * A's single-active-owner MVP cap, Section 2's {@code company_users}
     * "Business constraints left to the application layer" note).
     */
    @Transactional
    public CompanyUserResponse inviteStaff(UUID actingUserId, InviteStaffRequest request) {
        CompanyUser acting = getActiveMembershipOrThrow(actingUserId);
        requireOwner(acting);

        CompanyUserRole role;
        try {
            role = CompanyUserRole.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConflictException("role must be 'manager' or 'support'.");
        }
        if (role == CompanyUserRole.OWNER) {
            throw new ConflictException("MULTI_ADMIN_NOT_ENABLED: only one active Owner is supported per company at MVP.");
        }
        if (companyUserRepository.existsByCompanyIdAndUserIdAndStatus(acting.getCompanyId(), request.userId(), "active")) {
            throw new ConflictException("This user is already active staff for this company.");
        }

        CompanyUser invited = companyUserRepository.save(CompanyUser.invite(acting.getCompanyId(), request.userId(), role, actingUserId));
        return toResponse(invited);
    }

    // --- Phase 8 admin entry points (called by admin.service.AdminService — see ReportService's class doc for why that composition lives in admin, not here) ---

    /** {@code GET /admin/companies} (API Spec Section 16) — the business-verification review queue, always the pending ({@code UNDER_REVIEW}) ones (Operations Module A: "Application → Under Review → Verified/Rejected"). */
    @Transactional
    public CursorPageResponse<CompanyVerificationQueueEntry> getVerificationQueue(String cursor, int limit) {
        int effectiveLimit = limit <= 0 ? 20 : limit;
        int offset = OffsetCursor.decode(cursor);
        var page = companyVerificationRepository.findByStatusOrderByCreatedAtAsc(
                CompanyVerificationStatus.UNDER_REVIEW,
                PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit));
        var items = page.getContent().stream().map(v -> new CompanyVerificationQueueEntry(
                v.getId(), v.getCompanyId(), getCompanyOrThrow(v.getCompanyId()).getDisplayName(),
                v.getSubmittedDocuments(), v.getStatus().name(), v.isReverification(), v.getCreatedAt())).toList();
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    /**
     * {@code POST /admin/companies/{id}/verify} (API Spec Section 16,
     * capabilities table: "Moderator review/recommend, Admin final decision"
     * — the {@code ADMIN}-only gate is enforced by {@code AdminService}, not
     * here). Approves the company's latest pending verification attempt and
     * flips {@link TravelCompany} itself to {@code VERIFIED} atomically.
     */
    @Transactional
    public CompanyResponse adminVerify(UUID companyId, UUID reviewerId, String notes) {
        TravelCompany company = getCompanyOrThrow(companyId);
        CompanyVerification verification = companyVerificationRepository.findFirstByCompanyIdOrderByCreatedAtDesc(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company verification", companyId));
        verification.approve(reviewerId, notes);
        companyVerificationRepository.save(verification);
        company.verify();
        return toResponse(travelCompanyRepository.save(company));
    }

    /** {@code POST /admin/companies/{id}/verify}'s rejection branch — same endpoint, opposite decision (API Spec's {@code varies} request/response notation covers this). */
    @Transactional
    public CompanyResponse adminRejectVerification(UUID companyId, UUID reviewerId, String notes) {
        TravelCompany company = getCompanyOrThrow(companyId);
        CompanyVerification verification = companyVerificationRepository.findFirstByCompanyIdOrderByCreatedAtDesc(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company verification", companyId));
        verification.reject(reviewerId, notes);
        companyVerificationRepository.save(verification);
        company.reject();
        return toResponse(travelCompanyRepository.save(company));
    }

    /** {@code POST /admin/companies/{id}/suspend} (API Spec Section 16) — Operations Module A's Company Suspension feature: hides active listings, leaves already-committed travellers on open trips untouched (no trip-level cascade here — see this method's own doc vs. {@link #adminRemove}). */
    @Transactional
    public CompanyResponse adminSuspend(UUID companyId, String reason) {
        TravelCompany company = getCompanyOrThrow(companyId);
        company.suspend(reason);
        return toResponse(travelCompanyRepository.save(company));
    }

    /** Escalation tier: "repeat violation escalates to Removed, which DOES force-cancel open trips" (Operations Module A) — the force-cancel cascade across this company's open trips is {@code AdminService}'s job (composes {@code TripService}), not this module's. */
    @Transactional
    public CompanyResponse adminRemove(UUID companyId, String reason) {
        TravelCompany company = getCompanyOrThrow(companyId);
        company.remove(reason);
        return toResponse(travelCompanyRepository.save(company));
    }

    /** {@code GET /admin/users/{id}}-style detail read, but for a company — used by {@code AdminService} composition. */
    public CompanyResponse getForAdmin(UUID companyId) {
        return toResponse(getCompanyOrThrow(companyId));
    }

    /** {@code GET /admin/dashboard}'s pending-company-verifications count (Phase 8). */
    public long countPendingVerifications() {
        return companyVerificationRepository.countByStatus(CompanyVerificationStatus.UNDER_REVIEW);
    }

    // --- cross-module entry points (called directly by trip/review — see this class's doc) ---

    /** "Which company do I administer" — used to compose {@code GET /companies/me/trips} at the {@code TripController} layer. */
    public UUID getMyCompanyId(UUID userId) {
        return getActiveMembershipOrThrow(userId).getCompanyId();
    }

    /** Fix #4 (DB Review, MEDIUM): a Verified Partner Trip's organizer must be an active staff member of its company — cannot be expressed as a CHECK constraint (cross-table), so {@code TripService} calls this before creating one. */
    public void assertActiveMember(UUID companyId, UUID userId) {
        if (!companyUserRepository.existsByCompanyIdAndUserIdAndStatus(companyId, userId, "active")) {
            throw new ForbiddenException("You are not an active staff member of this company.");
        }
    }

    /**
     * Gates Verified Partner Trip creation on the company actually being
     * {@code VERIFIED} — an unverified/suspended/removed company must never
     * gain commercial listing capability (Operations Module A's "absolute"
     * Community/Company boundary). See this class's doc for the dev-only
     * bypass and why reaching {@code VERIFIED} for real needs Phase 8.
     */
    public void assertVerified(UUID companyId) {
        if (!enforceCompanyVerification) {
            return;
        }
        TravelCompany company = getCompanyOrThrow(companyId);
        if (!company.isVerified()) {
            throw new ForbiddenException("This company is not yet a Verified Partner — it cannot publish trips.");
        }
    }

    /** Verified Partner Trip organizer branding (Operations Module A: the traveller sees the Company, never a named employee) — see {@link CompanySummary}'s doc. */
    public CompanySummary getSummary(UUID companyId) {
        TravelCompany company = getCompanyOrThrow(companyId);
        return new CompanySummary(company.getId(), company.getDisplayName(), company.getLogoUrl(), company.isVerified());
    }

    /**
     * Cross-module entry point for {@code trip.service.TripService}'s public
     * discovery queries (Explore, Home's "Trips For You", and the general
     * trip list) — Operations Module A's Company Suspension rule requires a
     * Suspended (or escalated Removed) Company's trips to disappear from
     * those rankings immediately, without deleting or otherwise mutating the
     * trip rows themselves ("without deleting the listings, so reinstatement
     * doesn't require republishing"). This was a real, flagged gap through
     * Phase 8 — {@code CompanyService#adminSuspend} only ever flipped the
     * Company's own status, with no corresponding filter on the {@code trip}
     * side. Deliberately returns ids rather than a boolean-per-id check, so
     * {@code TripService} can compose one {@code companyId NOT IN (...)}
     * specification rather than querying per-row.
     */
    public List<UUID> getDiscoveryExcludedCompanyIds() {
        return travelCompanyRepository.findIdByStatusIn(DISCOVERY_EXCLUDED_STATUSES);
    }

    // --- internal ---------------------------------------------------------------

    private TravelCompany getCompanyOrThrow(UUID companyId) {
        return travelCompanyRepository.findById(companyId).orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
    }

    private CompanyUser getActiveMembershipOrThrow(UUID userId) {
        return companyUserRepository.findFirstByUserIdAndStatus(userId, "active")
                .orElseThrow(() -> new ForbiddenException("You are not staff of any Travel Company."));
    }

    private void requireOwner(CompanyUser membership) {
        if (membership.getRole() != CompanyUserRole.OWNER) {
            throw new ForbiddenException("Only the company's Owner can do this.");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize submitted documents.", e);
        }
    }

    private CompanyResponse toResponse(TravelCompany c) {
        return new CompanyResponse(
                c.getId(), c.getDisplayName(), c.getLegalName(), c.getRegistrationNumber(), c.getGstNumber(),
                c.getLogoUrl(), c.getDescription(), c.getWebsiteUrl(), c.getSupportEmail(), c.getSupportPhone(),
                c.getCancellationPolicy(), c.getStatus().name(), c.getSuspendedAt(), c.getSuspensionReason(), c.getCreatedAt());
    }

    private CompanyUserResponse toResponse(CompanyUser cu) {
        return new CompanyUserResponse(cu.getId(), cu.getCompanyId(), cu.getUserId(), cu.getRole().name(), cu.getStatus(), cu.getCreatedAt());
    }
}
