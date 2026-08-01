package com.gotogether.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gotogether.admin.dto.AdminDashboardResponse;
import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.admin.dto.AdminUserTrustDetailResponse;
import com.gotogether.admin.dto.AuditLogResponse;
import com.gotogether.admin.entity.AuditAction;
import com.gotogether.admin.entity.AuditLog;
import com.gotogether.admin.repository.AuditLogRepository;
import com.gotogether.chat.service.ChatService;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.company.dto.CompanyResponse;
import com.gotogether.company.dto.CompanyVerificationQueueEntry;
import com.gotogether.company.service.CompanyService;
import com.gotogether.report.dto.ReportResponse;
import com.gotogether.report.entity.ReportEntityType;
import com.gotogether.report.entity.ReportPriority;
import com.gotogether.report.entity.ReportResolutionAction;
import com.gotogether.report.entity.ReportStatus;
import com.gotogether.report.service.ReportService;
import com.gotogether.review.service.ReviewService;
import com.gotogether.trip.dto.CancelTripRequest;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.dto.TrustScoreHistoryEntry;
import com.gotogether.trust.service.TrustService;
import com.gotogether.user.dto.AdminUserDetailResponse;
import com.gotogether.user.dto.UserResponse;
import com.gotogether.user.dto.VerificationQueueEntry;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.service.UserService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code admin} module's orchestration layer — Trust &amp; Safety
 * Operations (Business Rules Operations Modules B/C, API Specification
 * Section 16). Unlike every other module's {@code Service}, this one is
 * deliberately an <b>orchestrator</b>: it composes {@code report}, {@code
 * user}, {@code trip}, {@code review}, {@code company}, {@code trust}, and
 * {@code chat} directly, because nothing depends back on {@code admin} (it
 * is the most "downstream" module in the system, more so even than {@code
 * trust} — see that class's doc) so no cycle risk exists no matter how much
 * it depends on. This is the same "composition lives in whichever module
 * can safely depend on both sides" reasoning used throughout this codebase,
 * just concentrated in one place because {@code admin} touches almost
 * everything by nature of what an Admin Panel is (see {@code
 * ReportService}'s class doc for why the report-resolution composition
 * specifically was deliberately kept out of {@code report} itself).
 *
 * <p><b>Role gating</b> lives here, not behind {@code @PreAuthorize} —
 * matching the existing {@code TripService.cancel}/moderator-override
 * pattern (role is passed in from {@code UserPrincipal}, checked with a
 * plain {@code if}, no Spring Security method-security annotations exist
 * anywhere else in this codebase). Per Operations Module C's Capabilities
 * table: most actions are {@code MODERATOR|ADMIN}; {@code ADMIN}-only are
 * Trip force-cancel, Company verify/reject decisions, Trust Score
 * unfreeze, and — specifically inside {@link #resolveReport} — any
 * resolution whose ladder tier is {@code SUSPENDED}/{@code REMOVED} against
 * a {@code user}/{@code trip}/{@code review}-typed report, since resolving
 * one of those cascades into force-cancelling the target's own open trips
 * (Operations Module B's Suspended tier: "auto-cancel with notification if
 * Organizer") — exactly the "force-cancel is admin-only" error the API
 * Specification's Section 16 table calls out on that one row. The plain
 * {@code POST /admin/users/{id}/restrict}/{@code /suspend} endpoints do
 * <b>not</b> cascade to trip force-cancellation (a deliberate scoping
 * choice — Section 16's table attaches no such error/role note to those two
 * rows, unlike the resolve-report row), so they stay {@code MODERATOR|ADMIN}
 * with no side effects beyond the account status change.
 *
 * <p><b>Scoped down / flagged, not silently invented:</b>
 * <ul>
 * <li>{@code GET /admin/analytics} is not implemented at all — Business
 * Rules Operations Module D ("Analytics &amp; Reporting") is Phase 9's
 * {@code analytics} module scope, confirmed during this phase's docs review
 * (Operations doc page 5). {@link #getDashboard} is scoped down to
 * open-queue counts for the same reason — see {@link AdminDashboardResponse}'s
 * doc.</li>
 * <li>A {@code report.entity.ReportEntityType#MESSAGE} report's {@code
 * CONTENT_REMOVED} resolution calls {@code ChatService#deleteMessage}
 * (already moderator-aware since Phase 4), but any account-level ladder
 * action (WARNED/RESTRICTED/SUSPENDED/REMOVED) against a MESSAGE report has
 * no way to resolve to a target user — {@code chat.service.ChatService}
 * exposes no sender-lookup entry point — so those combinations throw a
 * clear, documented error rather than guessing or silently no-op'ing.</li>
 * <li>{@code audit_action} (V1 migration) has no distinct "unfrozen" value
 * separate from {@link AuditAction#TRUST_SCORE_FROZEN}, and no dedicated
 * value at all for a Company being rejected/removed (only {@code
 * company_verified}/{@code company_suspended} exist) — those events reuse
 * the closest existing enum value, distinguished by reading {@code
 * new_value} on the audit row itself. Adding new DB enum values is outside
 * this pass's scope (a schema change, not an application-layer one).</li>
 * </ul>
 */
@Service
public class AdminService {

    /**
     * Reports & safety violations penalty magnitude per enforcement-ladder
     * tier (Trust & Discovery Module A's -15% weight component) — no exact
     * point values are given anywhere in the doc set, so these are a
     * reasonable, undocumented-elsewhere default (same "adopted here since
     * never formally resolved" precedent as {@code TrustService}'s own
     * {@code ANOMALY_THRESHOLD}).
     */
    private static final Map<ReportResolutionAction, BigDecimal> REPORTS_PENALTY = Map.of(
            ReportResolutionAction.WARNED, new BigDecimal("-0.3"),
            ReportResolutionAction.RESTRICTED, new BigDecimal("-0.7"),
            ReportResolutionAction.CONTENT_REMOVED, new BigDecimal("-0.2"),
            ReportResolutionAction.SUSPENDED, new BigDecimal("-1.5"),
            ReportResolutionAction.REMOVED, new BigDecimal("-3.0"));

    /** Operations Module C's Capabilities table: "Analytics: full KPI dashboards; Admin full, Moderator safety-relevant subset only." {@code trust_score_distribution} is the one metric this pass considers safety-relevant (Trust Score bands feed enforcement decisions); {@code event_counts}/{@code signups} are general product metrics, Admin-only here. */
    private static final Set<String> MODERATOR_ALLOWED_METRICS = Set.of("trust_score_distribution");

    private final ReportService reportService;
    private final UserService userService;
    private final TripService tripService;
    private final ReviewService reviewService;
    private final CompanyService companyService;
    private final TrustService trustService;
    private final ChatService chatService;
    private final AnalyticsService analyticsService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminService(
            ReportService reportService, UserService userService, TripService tripService, ReviewService reviewService,
            CompanyService companyService, TrustService trustService, ChatService chatService,
            AnalyticsService analyticsService, AuditLogRepository auditLogRepository) {
        this.reportService = reportService;
        this.userService = userService;
        this.tripService = tripService;
        this.reviewService = reviewService;
        this.companyService = companyService;
        this.trustService = trustService;
        this.chatService = chatService;
        this.analyticsService = analyticsService;
        this.auditLogRepository = auditLogRepository;
    }

    // --- Dashboard --------------------------------------------------------------

    public AdminDashboardResponse getDashboard(AccountRole role) {
        requireModeratorOrAdmin(role);
        var counts = reportService.getDashboardCounts();
        return new AdminDashboardResponse(
                counts.openTotal(), counts.emergency(), counts.safety(),
                userService.countPendingVerifications(), companyService.countPendingVerifications());
    }

    // --- Reports ------------------------------------------------------------

    public CursorPageResponse<ReportResponse> getReportsQueue(AccountRole role, String status, String priority, UUID assignedModeratorId, String cursor, int limit) {
        requireModeratorOrAdmin(role);
        ReportStatus parsedStatus = status == null ? null : ReportStatus.valueOf(status.toUpperCase());
        ReportPriority parsedPriority = priority == null ? null : ReportPriority.valueOf(priority.toUpperCase());
        return reportService.getQueue(parsedStatus, parsedPriority, assignedModeratorId, cursor, limit);
    }

    /** {@code POST /admin/reports/{id}/resolve} — see this class's doc for the full dispatch/role-gating design. */
    @Transactional
    public ReportResponse resolveReport(UUID actingUserId, AccountRole role, UUID reportId, String resolutionActionRaw, String resolutionNotes) {
        requireModeratorOrAdmin(role);
        ReportResolutionAction action;
        try {
            action = ReportResolutionAction.valueOf(resolutionActionRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnprocessableEntityException("'" + resolutionActionRaw + "' is not a recognised resolution action.");
        }

        ReportResponse before = reportService.get(reportId);
        boolean isEscalatedTier = action == ReportResolutionAction.SUSPENDED || action == ReportResolutionAction.REMOVED;
        boolean cascadesTripForceCancel = isEscalatedTier
                && (before.entityType().equals(ReportEntityType.USER.name())
                    || before.entityType().equals(ReportEntityType.TRIP.name())
                    || before.entityType().equals(ReportEntityType.REVIEW.name()));
        if (cascadesTripForceCancel) {
            requireAdmin(role, "force-cancel is admin-only");
        }

        ReportResponse resolved = reportService.resolve(actingUserId, reportId, action, resolutionNotes);
        if (action != ReportResolutionAction.DISMISSED) {
            dispatchEnforcement(actingUserId, role, resolved, action, resolutionNotes);
        }
        return resolved;
    }

    /** Dispatches the actual side effect a resolved report implies — see this class's doc's bullet list for the intentionally-unsupported combinations. */
    private void dispatchEnforcement(UUID actingUserId, AccountRole role, ReportResponse report, ReportResolutionAction action, String notes) {
        ReportEntityType entityType = ReportEntityType.valueOf(report.entityType());
        UUID entityId = report.entityId();

        if (action == ReportResolutionAction.CONTENT_REMOVED) {
            switch (entityType) {
                case TRIP -> tripService.adminHide(entityId);
                case REVIEW -> reviewService.adminRemove(entityId, notes);
                case MESSAGE -> chatService.deleteMessage(actingUserId, role, entityId);
                default -> throw new UnprocessableEntityException(
                        "content_removed is not a valid resolution for a '" + report.entityType() + "' report.");
            }
            return;
        }

        // WARNED / RESTRICTED / SUSPENDED / REMOVED — account-ladder actions.
        UUID targetUserId = switch (entityType) {
            case USER -> entityId;
            case TRIP -> resolveTripOrganizer(entityId);
            case REVIEW -> reviewService.getReviewerId(entityId);
            case COMPANY -> null; // handled separately below
            case MESSAGE -> throw new UnprocessableEntityException(
                    "MESSAGE reports have no sender lookup wired yet — only content_removed is supported for this entity type (see AdminService's class doc).");
        };

        if (entityType == ReportEntityType.COMPANY) {
            applyCompanyLadderAction(entityId, action, notes);
            return;
        }

        applyUserLadderAction(targetUserId, action, notes);
    }

    private void applyUserLadderAction(UUID userId, ReportResolutionAction action, String notes) {
        switch (action) {
            case WARNED -> { /* Operations Module B: "in-app notice, no restriction" — no status change, audit row (below) is the only record. */ }
            case RESTRICTED -> userService.adminRestrict(userId);
            case SUSPENDED -> {
                userService.adminSuspend(userId);
                cancelOpenOwnedTrips(userId, notes);
            }
            case REMOVED -> {
                userService.adminRemove(userId);
                cancelOpenOwnedTrips(userId, notes);
            }
            default -> { /* DISMISSED/CONTENT_REMOVED handled by callers */ }
        }
        BigDecimal penalty = REPORTS_PENALTY.get(action);
        if (penalty != null) {
            trustService.applyReportsPenalty(userId, penalty, "Report resolved: " + action.name().toLowerCase());
        }
    }

    private void applyCompanyLadderAction(UUID companyId, ReportResolutionAction action, String notes) {
        switch (action) {
            case WARNED, RESTRICTED -> { /* no CompanyStatus tier maps to these — audit row is the only record, matching the User ladder's own Warning-tier no-op. */ }
            case SUSPENDED -> companyService.adminSuspend(companyId, notes);
            case REMOVED -> {
                companyService.adminRemove(companyId, notes);
                for (UUID tripId : tripService.listCompanyTripIds(companyId)) {
                    cancelIfOpen(tripId, "Travel Company removed: " + notes);
                }
            }
            default -> { }
        }
    }

    private UUID resolveTripOrganizer(UUID tripId) {
        return tripService.getSummary(tripId).organizerId();
    }

    private void cancelOpenOwnedTrips(UUID organizerId, String reason) {
        List<TripSummary> ownTrips = tripService.listOwnTrips(organizerId);
        for (TripSummary trip : ownTrips) {
            if (trip.status() != TripStatus.CANCELLED && trip.status() != TripStatus.COMPLETED && trip.status() != TripStatus.ARCHIVED) {
                cancelIfOpen(trip.id(), reason);
            }
        }
    }

    private void cancelIfOpen(UUID tripId, String reason) {
        try {
            tripService.cancel(null, AccountRole.ADMIN, tripId, new CancelTripRequest(
                    reason == null || reason.isBlank() ? "Cancelled by platform enforcement action." : reason));
        } catch (RuntimeException ignored) {
            // Already terminal or otherwise not cancellable — the ladder's "existing commitments unaffected" edge case; not a caller-visible failure.
        }
    }

    // --- Users / verifications -----------------------------------------------

    public AdminUserTrustDetailResponse getUserDetail(AccountRole role, UUID userId) {
        requireModeratorOrAdmin(role);
        AdminUserDetailResponse account = userService.getAdminDetail(userId);
        var trustScore = trustService.getPublicBreakdown(userId);
        var history = trustService.getHistory(userId, null, 20).items();
        return new AdminUserTrustDetailResponse(account, trustScore, history);
    }

    @Transactional
    public UserResponse restrictUser(UUID actorId, AccountRole role, UUID userId, String reason) {
        requireModeratorOrAdmin(role);
        UserResponse before = userService.getMe(userId);
        UserResponse after = userService.adminRestrict(userId);
        writeAudit(actorId, AuditAction.USER_RESTRICTED, "users", userId, before.status(), after.status(), reason);
        return after;
    }

    @Transactional
    public UserResponse suspendUser(UUID actorId, AccountRole role, UUID userId, String reason) {
        requireModeratorOrAdmin(role);
        UserResponse before = userService.getMe(userId);
        UserResponse after = userService.adminSuspend(userId);
        writeAudit(actorId, AuditAction.USER_SUSPENDED, "users", userId, before.status(), after.status(), reason);
        return after;
    }

    public CursorPageResponse<VerificationQueueEntry> getVerificationsQueue(AccountRole role, String cursor, int limit) {
        requireModeratorOrAdmin(role);
        return userService.getVerificationQueue(cursor, limit);
    }

    @Transactional
    public void approveVerification(UUID actorId, AccountRole role, UUID verificationId) {
        requireModeratorOrAdmin(role);
        var result = userService.approveVerification(verificationId, actorId);
        writeAudit(actorId, AuditAction.VERIFICATION_APPROVED, "verifications", verificationId, null, result.status().name(), null);
        analyticsService.record("verification_approved", null, "verifications", verificationId, null);
    }

    @Transactional
    public void rejectVerification(UUID actorId, AccountRole role, UUID verificationId, String rejectionReasonRaw) {
        requireModeratorOrAdmin(role);
        var result = userService.rejectVerification(verificationId, actorId, rejectionReasonRaw);
        writeAudit(actorId, AuditAction.VERIFICATION_REJECTED, "verifications", verificationId, null, result.status().name(), rejectionReasonRaw);
    }

    // --- Companies ------------------------------------------------------------

    public CursorPageResponse<CompanyVerificationQueueEntry> getCompaniesQueue(AccountRole role, String cursor, int limit) {
        requireModeratorOrAdmin(role);
        return companyService.getVerificationQueue(cursor, limit);
    }

    @Transactional
    public CompanyResponse verifyCompany(UUID actorId, AccountRole role, UUID companyId, String notes) {
        requireAdmin(role, "company verification decisions are admin-only (Operations Module A: 'Admin final decision')");
        CompanyResponse result = companyService.adminVerify(companyId, actorId, notes);
        writeAudit(actorId, AuditAction.COMPANY_VERIFIED, "travel_companies", companyId, null, result.status(), notes);
        analyticsService.record("verification_approved", null, "travel_companies", companyId, null);
        return result;
    }

    @Transactional
    public CompanyResponse rejectCompanyVerification(UUID actorId, AccountRole role, UUID companyId, String notes) {
        requireAdmin(role, "company verification decisions are admin-only (Operations Module A: 'Admin final decision')");
        CompanyResponse result = companyService.adminRejectVerification(companyId, actorId, notes);
        writeAudit(actorId, AuditAction.VERIFICATION_REJECTED, "travel_companies", companyId, null, result.status(), notes);
        return result;
    }

    @Transactional
    public CompanyResponse suspendCompany(UUID actorId, AccountRole role, UUID companyId, String reason) {
        requireModeratorOrAdmin(role);
        CompanyResponse result = companyService.adminSuspend(companyId, reason);
        writeAudit(actorId, AuditAction.COMPANY_SUSPENDED, "travel_companies", companyId, null, result.status(), reason);
        return result;
    }

    // --- Trips ------------------------------------------------------------

    @Transactional
    public TripResponse hideTrip(UUID actorId, AccountRole role, UUID tripId, String reason) {
        requireModeratorOrAdmin(role);
        TripResponse result = tripService.adminHide(tripId);
        writeAudit(actorId, AuditAction.TRIP_HIDDEN, "trips", tripId, null, result.status().name(), reason);
        return result;
    }

    @Transactional
    public TripResponse forceCancelTrip(UUID actorId, AccountRole role, UUID tripId, String reason) {
        requireAdmin(role, "force-cancel is admin-only");
        TripResponse result = tripService.cancel(actorId, AccountRole.ADMIN, tripId, new CancelTripRequest(reason));
        writeAudit(actorId, AuditAction.TRIP_FORCE_CANCELLED, "trips", tripId, null, result.status().name(), reason);
        return result;
    }

    // --- Trust score oversight -------------------------------------------------

    /** Operations Module C: "Admin view+freeze/unfreeze on anomaly" — {@code ADMIN}-only, Moderator is view-only for this area. */
    @Transactional
    public void unfreezeTrustScore(UUID actorId, AccountRole role, UUID userId, String reason) {
        requireAdmin(role, "trust score freeze/unfreeze is admin-only (Operations Module C)");
        trustService.unfreeze(userId, actorId, reason);
        writeAudit(actorId, AuditAction.TRUST_SCORE_FROZEN, "trust_scores", userId, "frozen", "unfrozen: " + reason, reason);
    }

    // --- Analytics --------------------------------------------------------------

    /**
     * {@code GET /admin/analytics} (Phase 9, API Spec Section 16) — see
     * {@code AnalyticsService}'s class doc for exactly which {@code metric}
     * values exist and which Metric Set categories remain unimplemented.
     * Moderator is restricted to {@link #MODERATOR_ALLOWED_METRICS}
     * (Operations Module C: "Admin full, Moderator safety-relevant subset
     * only").
     */
    public Map<String, Object> getAnalytics(AccountRole role, String metric, OffsetDateTime dateFrom, OffsetDateTime dateTo) {
        requireModeratorOrAdmin(role);
        if (role == AccountRole.MODERATOR && !MODERATOR_ALLOWED_METRICS.contains(metric)) {
            throw new ForbiddenException("INSUFFICIENT_ROLE: moderators can only view safety-relevant analytics (" + MODERATOR_ALLOWED_METRICS + ").");
        }
        return analyticsService.getMetric(metric, dateFrom, dateTo);
    }

    // --- Audit logs -------------------------------------------------------------

    /** {@code GET /admin/audit-logs} — Admin sees everyone's (optionally filtered), Moderator sees only their own (Operations Module C). */
    public CursorPageResponse<AuditLogResponse> getAuditLogs(UUID callerId, AccountRole role, UUID actorIdFilter, String entityType, String cursor, int limit) {
        requireModeratorOrAdmin(role);
        UUID effectiveActorId = actorIdFilter;
        if (role == AccountRole.MODERATOR) {
            if (actorIdFilter != null && !actorIdFilter.equals(callerId)) {
                throw new ForbiddenException("CANNOT_VIEW_OTHERS_LOG: moderators can only view their own action log.");
            }
            effectiveActorId = callerId;
        }

        int effectiveLimit = limit <= 0 ? 20 : limit;
        int offset = OffsetCursor.decode(cursor);
        var page = auditLogRepository.search(effectiveActorId, entityType, PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit));
        List<AuditLogResponse> items = page.getContent().stream().map(this::toAuditResponse).toList();
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    // --- internal ---------------------------------------------------------------

    private void requireModeratorOrAdmin(AccountRole role) {
        if (role != AccountRole.MODERATOR && role != AccountRole.ADMIN) {
            throw new ForbiddenException("INSUFFICIENT_ROLE: moderator or admin access required.");
        }
    }

    private void requireAdmin(AccountRole role, String context) {
        if (role != AccountRole.ADMIN) {
            throw new ForbiddenException("INSUFFICIENT_ROLE: admin access required — " + context + ".");
        }
    }

    private void writeAudit(UUID actorId, AuditAction action, String entityType, UUID entityId, Object oldValue, Object newValue, String reason) {
        String oldJson = toJson(oldValue);
        String newJson = toJson(reason == null ? Map.of("value", String.valueOf(newValue)) : Map.of("value", String.valueOf(newValue), "reason", reason));
        auditLogRepository.save(AuditLog.record(actorId, action, entityType, entityId, oldJson, newJson));
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private AuditLogResponse toAuditResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorId(), log.getAction().name(), log.getEntityType(), log.getEntityId(),
                log.getOldValueJson(), log.getNewValueJson(), log.getCreatedAt());
    }
}
