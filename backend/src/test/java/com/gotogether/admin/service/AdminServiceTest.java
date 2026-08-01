package com.gotogether.admin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.admin.entity.AuditLog;
import com.gotogether.admin.repository.AuditLogRepository;
import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.chat.service.ChatService;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.company.dto.CompanyResponse;
import com.gotogether.company.service.CompanyService;
import com.gotogether.report.dto.ReportResponse;
import com.gotogether.report.service.ReportService;
import com.gotogether.review.service.ReviewService;
import com.gotogether.trip.dto.TripResponse;
import com.gotogether.trip.service.TripService;
import com.gotogether.trust.service.TrustService;
import com.gotogether.user.dto.UserResponse;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ReportService reportService;
    @Mock private UserService userService;
    @Mock private TripService tripService;
    @Mock private ReviewService reviewService;
    @Mock private CompanyService companyService;
    @Mock private TrustService trustService;
    @Mock private ChatService chatService;
    @Mock private AnalyticsService analyticsService;
    @Mock private AuditLogRepository auditLogRepository;

    private AdminService adminService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                reportService, userService, tripService, reviewService, companyService, trustService, chatService,
                analyticsService, auditLogRepository);
        lenient().when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tripService.listOwnTrips(any())).thenReturn(List.of());
    }

    private ReportResponse reportOf(String entityType, UUID entityId, String status) {
        return new ReportResponse(reportId, UUID.randomUUID(), entityType, entityId, "harassment", null, status, "safety", null, null, null, OffsetDateTime.now(), null);
    }

    private UserResponse userResponse(UserStatus status) {
        return new UserResponse(targetUserId, "+911234567890", null, status, VerificationLevel.ID_APPROVED, AccountRole.INDIVIDUAL, OffsetDateTime.now());
    }

    // --- role gating ---------------------------------------------------------

    @Test
    void getDashboardThrowsForAnIndividualCaller() {
        assertThatThrownBy(() -> adminService.getDashboard(AccountRole.INDIVIDUAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INSUFFICIENT_ROLE");
    }

    @Test
    void forceCancelTripThrowsForAModeratorCallerEvenThoughTripCancelItselfAllowsModerators() {
        assertThatThrownBy(() -> adminService.forceCancelTrip(actorId, AccountRole.MODERATOR, tripId, "unsafe"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INSUFFICIENT_ROLE");
        verify(tripService, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void forceCancelTripSucceedsForAnAdmin() {
        when(tripService.cancel(eq(actorId), eq(AccountRole.ADMIN), eq(tripId), any())).thenReturn(minimalCancelledTripResponse());

        adminService.forceCancelTrip(actorId, AccountRole.ADMIN, tripId, "unsafe behaviour substantiated");

        verify(tripService).cancel(eq(actorId), eq(AccountRole.ADMIN), eq(tripId), any());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    /** {@link TripResponse} has 28 positional fields including two primitive {@code short}s (min/max group size) — a minimal all-null/false instance still needs real values for those two. */
    private TripResponse minimalCancelledTripResponse() {
        return new TripResponse(
                tripId, UUID.randomUUID(), null, null, null, com.gotogether.trip.entity.TripStatus.CANCELLED, null,
                null, null, null, false, null, null, null, null, null, (short) 2, (short) 6, false, false, null,
                null, null, null, null, List.of(), null, null);
    }

    @Test
    void verifyCompanyThrowsForAModerator() {
        assertThatThrownBy(() -> adminService.verifyCompany(actorId, AccountRole.MODERATOR, companyId, "looks good"))
                .isInstanceOf(ForbiddenException.class);
        verify(companyService, never()).adminVerify(any(), any(), any());
    }

    @Test
    void suspendCompanyIsAllowedForAModerator() {
        when(companyService.adminSuspend(companyId, "complaint")).thenReturn(companyResponseWithStatus("SUSPENDED"));

        adminService.suspendCompany(actorId, AccountRole.MODERATOR, companyId, "complaint");

        verify(companyService).adminSuspend(companyId, "complaint");
    }

    @Test
    void restrictUserSucceedsForAModeratorWithNoTripCascade() {
        when(userService.getMe(targetUserId)).thenReturn(userResponse(UserStatus.VERIFIED));
        when(userService.adminRestrict(targetUserId)).thenReturn(userResponse(UserStatus.RESTRICTED));

        adminService.restrictUser(actorId, AccountRole.MODERATOR, targetUserId, "repeat minor issue");

        verify(userService).adminRestrict(targetUserId);
        verify(tripService, never()).listOwnTrips(any());
    }

    @Test
    void suspendUserDirectEndpointSucceedsForAModeratorWithNoTripCascade() {
        when(userService.getMe(targetUserId)).thenReturn(userResponse(UserStatus.RESTRICTED));
        when(userService.adminSuspend(targetUserId)).thenReturn(userResponse(UserStatus.SUSPENDED));

        adminService.suspendUser(actorId, AccountRole.MODERATOR, targetUserId, "serious issue");

        verify(userService).adminSuspend(targetUserId);
        verify(tripService, never()).listOwnTrips(any());
    }

    @Test
    void unfreezeTrustScoreThrowsForAModerator() {
        assertThatThrownBy(() -> adminService.unfreezeTrustScore(actorId, AccountRole.MODERATOR, targetUserId, "reviewed, looks fine"))
                .isInstanceOf(ForbiddenException.class);
        verify(trustService, never()).unfreeze(any(), any(), any());
    }

    @Test
    void unfreezeTrustScoreSucceedsForAnAdminAndWritesAnAuditRow() {
        adminService.unfreezeTrustScore(actorId, AccountRole.ADMIN, targetUserId, "reviewed, looks fine");

        verify(trustService).unfreeze(targetUserId, actorId, "reviewed, looks fine");
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // --- analytics role gating (Phase 9) --------------------------------------

    @Test
    void getAnalyticsThrowsForAnIndividualCaller() {
        assertThatThrownBy(() -> adminService.getAnalytics(AccountRole.INDIVIDUAL, "signups", null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INSUFFICIENT_ROLE");
        verify(analyticsService, never()).getMetric(any(), any(), any());
    }

    @Test
    void getAnalyticsThrowsForAModeratorRequestingANonSafetyMetric() {
        assertThatThrownBy(() -> adminService.getAnalytics(AccountRole.MODERATOR, "signups", null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INSUFFICIENT_ROLE");
        verify(analyticsService, never()).getMetric(any(), any(), any());
    }

    @Test
    void getAnalyticsAllowsAModeratorToRequestTrustScoreDistribution() {
        when(analyticsService.getMetric(eq("trust_score_distribution"), any(), any())).thenReturn(Map.of("distribution", Map.of()));

        adminService.getAnalytics(AccountRole.MODERATOR, "trust_score_distribution", null, null);

        verify(analyticsService).getMetric(eq("trust_score_distribution"), any(), any());
    }

    @Test
    void getAnalyticsAllowsAnAdminToRequestAnyMetric() {
        when(analyticsService.getMetric(eq("event_counts"), any(), any())).thenReturn(Map.of("counts", Map.of()));

        adminService.getAnalytics(AccountRole.ADMIN, "event_counts", null, null);

        verify(analyticsService).getMetric(eq("event_counts"), any(), any());
    }

    // --- report resolution dispatch ------------------------------------------

    @Test
    void resolveReportThrowsForModeratorWhenActionEscalatesToSuspendedAgainstAUserReport() {
        when(reportService.get(reportId)).thenReturn(reportOf("USER", targetUserId, "OPEN"));

        assertThatThrownBy(() -> adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "suspended", "repeat offender"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INSUFFICIENT_ROLE");
        verify(reportService, never()).resolve(any(), any(), any(), any());
    }

    @Test
    void resolveReportWithWarnedIsAllowedForAModeratorAndAppliesASmallTrustPenalty() {
        when(reportService.get(reportId)).thenReturn(reportOf("USER", targetUserId, "OPEN"));
        when(reportService.resolve(eq(actorId), eq(reportId), any(), any())).thenReturn(reportOf("USER", targetUserId, "RESOLVED"));

        adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "warned", "first offense");

        verify(userService, never()).adminRestrict(any());
        verify(userService, never()).adminSuspend(any());
        verify(trustService).applyReportsPenalty(eq(targetUserId), eq(new BigDecimal("-0.3")), anyString());
    }

    @Test
    void resolveReportWithRestrictedCallsUserRestrictAndAppliesPenalty() {
        when(reportService.get(reportId)).thenReturn(reportOf("USER", targetUserId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("USER", targetUserId, "RESOLVED"));

        adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "restricted", "repeat minor issue");

        verify(userService).adminRestrict(targetUserId);
        verify(trustService).applyReportsPenalty(eq(targetUserId), eq(new BigDecimal("-0.7")), anyString());
    }

    @Test
    void resolveReportWithSuspendedRequiresAdminAndCascadesTripCancellationAndPenalty() {
        when(reportService.get(reportId)).thenReturn(reportOf("USER", targetUserId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("USER", targetUserId, "RESOLVED"));

        adminService.resolveReport(actorId, AccountRole.ADMIN, reportId, "suspended", "serious substantiated issue");

        verify(userService).adminSuspend(targetUserId);
        verify(tripService).listOwnTrips(targetUserId);
        verify(trustService).applyReportsPenalty(eq(targetUserId), eq(new BigDecimal("-1.5")), anyString());
    }

    @Test
    void resolveReportWithDismissedNeverDispatchesAnyEnforcementOrPenalty() {
        when(reportService.get(reportId)).thenReturn(reportOf("USER", targetUserId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("USER", targetUserId, "DISMISSED"));

        adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "dismissed", "no evidence");

        verify(userService, never()).adminRestrict(any());
        verify(userService, never()).adminSuspend(any());
        verify(trustService, never()).applyReportsPenalty(any(), any(), any());
    }

    @Test
    void resolveReportWithContentRemovedAgainstATripReportHidesTheTrip() {
        when(reportService.get(reportId)).thenReturn(reportOf("TRIP", tripId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("TRIP", tripId, "RESOLVED"));

        adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "content_removed", "inappropriate listing");

        verify(tripService).adminHide(tripId);
        verify(trustService).applyReportsPenalty(any(), eq(new BigDecimal("-0.2")), anyString());
    }

    @Test
    void resolveReportWithAnAccountLadderActionAgainstAMessageReportThrows() {
        UUID messageId = UUID.randomUUID();
        when(reportService.get(reportId)).thenReturn(reportOf("MESSAGE", messageId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("MESSAGE", messageId, "RESOLVED"));

        assertThatThrownBy(() -> adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "warned", "rude message"))
                .isInstanceOf(com.gotogether.common.exception.UnprocessableEntityException.class);
    }

    @Test
    void resolveReportWithContentRemovedAgainstAMessageReportDeletesIt() {
        UUID messageId = UUID.randomUUID();
        when(reportService.get(reportId)).thenReturn(reportOf("MESSAGE", messageId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("MESSAGE", messageId, "RESOLVED"));

        adminService.resolveReport(actorId, AccountRole.MODERATOR, reportId, "content_removed", "spam link");

        verify(chatService).deleteMessage(actorId, AccountRole.MODERATOR, messageId);
    }

    @Test
    void resolveReportWithRemovedAgainstACompanyReportCascadesToTripForceCancel() {
        UUID companyTripId = UUID.randomUUID();
        when(reportService.get(reportId)).thenReturn(reportOf("COMPANY", companyId, "OPEN"));
        when(reportService.resolve(any(), any(), any(), any())).thenReturn(reportOf("COMPANY", companyId, "RESOLVED"));
        when(companyService.adminRemove(companyId, "fraudulent operator")).thenReturn(companyResponseWithStatus("REMOVED"));
        when(tripService.listCompanyTripIds(companyId)).thenReturn(List.of(companyTripId));

        adminService.resolveReport(actorId, AccountRole.ADMIN, reportId, "removed", "fraudulent operator");

        verify(companyService).adminRemove(companyId, "fraudulent operator");
        verify(tripService).cancel(isNull(), eq(AccountRole.ADMIN), eq(companyTripId), any());
    }

    // --- audit log visibility -------------------------------------------------

    @Test
    void getAuditLogsThrowsWhenAModeratorRequestsSomeoneElsesLog() {
        UUID someoneElse = UUID.randomUUID();
        assertThatThrownBy(() -> adminService.getAuditLogs(actorId, AccountRole.MODERATOR, someoneElse, null, null, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("CANNOT_VIEW_OTHERS_LOG");
    }

    private CompanyResponse companyResponseWithStatus(String status) {
        return new CompanyResponse(companyId, "Summit Travel", "Summit Pvt Ltd", "REG-1", null, null, null, null,
                "a@b.com", "+911234567890", "policy", status, null, null, OffsetDateTime.now());
    }
}
