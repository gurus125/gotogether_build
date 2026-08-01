package com.gotogether.admin.controller;

import com.gotogether.admin.dto.AdminDashboardResponse;
import com.gotogether.admin.dto.AdminUserTrustDetailResponse;
import com.gotogether.admin.dto.AuditLogResponse;
import com.gotogether.admin.dto.CompanyVerificationDecisionRequest;
import com.gotogether.admin.dto.ReasonRequest;
import com.gotogether.admin.dto.ResolveReportRequest;
import com.gotogether.admin.dto.VerificationDecisionRequest;
import com.gotogether.admin.service.AdminService;
import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.company.dto.CompanyResponse;
import com.gotogether.company.dto.CompanyVerificationQueueEntry;
import com.gotogether.report.dto.ReportResponse;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.user.dto.UserResponse;
import com.gotogether.user.dto.VerificationQueueEntry;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin APIs (API Specification Section 16, Business Rules Operations
 * Module C). Every endpoint requires {@code MODERATOR} or {@code ADMIN} at
 * minimum; the finer-grained per-action split is enforced inside {@code
 * AdminService} — see that class's doc for the full reasoning.
 *
 * <p>{@code GET /admin/analytics} (added Phase 9) composes {@code
 * analytics.service.AnalyticsService} — see that class's doc for exactly
 * which metrics are supported versus scoped down from Operations Module D's
 * full Metric Set.
 *
 * <p>{@code POST /admin/users/{id}/trust-score/unfreeze}: Operations Module
 * C's capabilities table names "Admin view+freeze/unfreeze on anomaly" as a
 * capability, but Section 16's actual endpoint table never allocated it a
 * row. {@code AdminService#unfreezeTrustScore} has implemented the capability
 * correctly since Phase 8; this route was deliberately left unwired pending
 * a decision with the user (see {@code ReviewService}'s class doc for the
 * identical "the endpoint table is the complete contract, don't invent a new
 * path" reasoning already applied elsewhere) — wired now at the user's
 * explicit request, so it's a deliberate, flagged deviation from the literal
 * spec table rather than a silent one. Freezing itself stays automatic (an
 * anomaly-detection side effect inside {@code TrustService#recalculate}, not
 * a manual admin action) — there is no corresponding "freeze" endpoint.
 */
@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admin/dashboard")
    public AdminDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return adminService.getDashboard(principal.role());
    }

    @GetMapping("/admin/reports")
    public CursorPageResponse<ReportResponse> reportsQueue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status, @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return adminService.getReportsQueue(principal.role(), status, priority, assignedTo, cursor, limit);
    }

    @PostMapping("/admin/reports/{id}/resolve")
    public ReportResponse resolveReport(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody ResolveReportRequest request) {
        return adminService.resolveReport(principal.userId(), principal.role(), id, request.resolutionAction(), request.resolution());
    }

    @GetMapping("/admin/users/{id}")
    public AdminUserTrustDetailResponse userDetail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return adminService.getUserDetail(principal.role(), id);
    }

    @PostMapping("/admin/users/{id}/restrict")
    public UserResponse restrictUser(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return adminService.restrictUser(principal.userId(), principal.role(), id, request.reason());
    }

    @PostMapping("/admin/users/{id}/suspend")
    public UserResponse suspendUser(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return adminService.suspendUser(principal.userId(), principal.role(), id, request.reason());
    }

    /** See this class's doc for why this route exists despite Section 16's endpoint table having no row for it. */
    @PostMapping("/admin/users/{id}/trust-score/unfreeze")
    public void unfreezeTrustScore(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        adminService.unfreezeTrustScore(principal.userId(), principal.role(), id, request.reason());
    }

    @GetMapping("/admin/companies")
    public CursorPageResponse<CompanyVerificationQueueEntry> companiesQueue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return adminService.getCompaniesQueue(principal.role(), cursor, limit);
    }

    /** See {@link CompanyVerificationDecisionRequest}'s doc for why approve/reject share one endpoint. */
    @PostMapping("/admin/companies/{id}/verify")
    public CompanyResponse verifyCompany(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody(required = false) CompanyVerificationDecisionRequest request) {
        CompanyVerificationDecisionRequest body = request == null ? new CompanyVerificationDecisionRequest(null, null) : request;
        return body.isReject()
                ? adminService.rejectCompanyVerification(principal.userId(), principal.role(), id, body.notes())
                : adminService.verifyCompany(principal.userId(), principal.role(), id, body.notes());
    }

    @PostMapping("/admin/companies/{id}/suspend")
    public CompanyResponse suspendCompany(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return adminService.suspendCompany(principal.userId(), principal.role(), id, request.reason());
    }

    @GetMapping("/admin/verifications")
    public CursorPageResponse<VerificationQueueEntry> verificationsQueue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return adminService.getVerificationsQueue(principal.role(), cursor, limit);
    }

    @PostMapping("/admin/verifications/{id}/approve")
    public void approveVerification(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        adminService.approveVerification(principal.userId(), principal.role(), id);
    }

    @PostMapping("/admin/verifications/{id}/reject")
    public void rejectVerification(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody(required = false) VerificationDecisionRequest request) {
        String rejectionReason = request == null ? null : request.rejectionReason();
        adminService.rejectVerification(principal.userId(), principal.role(), id, rejectionReason);
    }

    @PostMapping("/admin/trips/{id}/hide")
    public TripResponse hideTrip(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return adminService.hideTrip(principal.userId(), principal.role(), id, request.reason());
    }

    @PostMapping("/admin/trips/{id}/force-cancel")
    public TripResponse forceCancelTrip(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return adminService.forceCancelTrip(principal.userId(), principal.role(), id, request.reason());
    }

    @GetMapping("/admin/analytics")
    public Map<String, Object> analytics(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam String metric,
            @RequestParam(required = false) OffsetDateTime dateFrom, @RequestParam(required = false) OffsetDateTime dateTo) {
        return adminService.getAnalytics(principal.role(), metric, dateFrom, dateTo);
    }

    @GetMapping("/admin/audit-logs")
    public CursorPageResponse<AuditLogResponse> auditLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return adminService.getAuditLogs(principal.userId(), principal.role(), actorId, entityType, cursor, limit);
    }
}
