package com.gotogether.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.RateLimitedException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.report.dto.CreateReportRequest;
import com.gotogether.report.entity.Report;
import com.gotogether.report.entity.ReportEntityType;
import com.gotogether.report.entity.ReportEvidence;
import com.gotogether.report.entity.ReportPriority;
import com.gotogether.report.entity.ReportReason;
import com.gotogether.report.entity.ReportResolutionAction;
import com.gotogether.report.repository.ReportEvidenceRepository;
import com.gotogether.report.repository.ReportRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ReportEvidenceRepository reportEvidenceRepository;

    private ReportService reportService;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, reportEvidenceRepository);
        lenient().when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateReportRequest validRequest() {
        return new CreateReportRequest("trip", entityId, "unsafe_behaviour", "Organizer left the group stranded.");
    }

    @Test
    void fileThrowsWhenRateLimitExceeded() {
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(reporterId), any(OffsetDateTime.class))).thenReturn(10L);

        assertThatThrownBy(() -> reportService.file(reporterId, validRequest()))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageContaining("REPORT_RATE_LIMITED");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void fileThrowsOnInvalidEntityType() {
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(reporterId), any(OffsetDateTime.class))).thenReturn(0L);
        var request = new CreateReportRequest("not_a_real_type", entityId, "spam", null);

        assertThatThrownBy(() -> reportService.file(reporterId, request))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("INVALID_ENTITY_TYPE");
    }

    @Test
    void fileThrowsOnInvalidReason() {
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(reporterId), any(OffsetDateTime.class))).thenReturn(0L);
        var request = new CreateReportRequest("trip", entityId, "not_a_real_reason", null);

        assertThatThrownBy(() -> reportService.file(reporterId, request)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void fileSucceedsWithRoutinePriority() {
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(reporterId), any(OffsetDateTime.class))).thenReturn(0L);

        var response = reportService.file(reporterId, validRequest());

        assertThat(response.priority()).isEqualTo("ROUTINE");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.entityType()).isEqualTo("TRIP");
    }

    @Test
    void fileEmergencyForcesEmergencyPriorityRegardlessOfInput() {
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(reporterId), any(OffsetDateTime.class))).thenReturn(0L);

        var response = reportService.fileEmergency(reporterId, validRequest());

        assertThat(response.priority()).isEqualTo("EMERGENCY");
    }

    @Test
    void addEvidenceThrowsWhenCallerIsNotTheReporter() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.file(reporterId, ReportEntityType.TRIP, entityId, ReportReason.FRAUD, null, ReportPriority.ROUTINE);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        UUID someoneElse = UUID.randomUUID();
        assertThatThrownBy(() -> reportService.addEvidence(someoneElse, reportId, "storage://x", "image/png", 1024))
                .isInstanceOf(ForbiddenException.class);
        verify(reportEvidenceRepository, never()).save(any());
    }

    @Test
    void addEvidenceSucceedsForTheOriginalReporter() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.file(reporterId, ReportEntityType.TRIP, entityId, ReportReason.FRAUD, null, ReportPriority.ROUTINE);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportEvidenceRepository.save(any(ReportEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = reportService.addEvidence(reporterId, reportId, "storage://evidence-1", "image/png", 2048);

        assertThat(response.storageKey()).isEqualTo("storage://evidence-1");
    }

    @Test
    void resolveThrowsWhenReportIsAlreadyResolved() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.file(reporterId, ReportEntityType.USER, entityId, ReportReason.HARASSMENT, null, ReportPriority.SAFETY);
        report.resolve(UUID.randomUUID(), ReportResolutionAction.WARNED, "already handled");
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.resolve(UUID.randomUUID(), reportId, ReportResolutionAction.RESTRICTED, "again"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolveWithDismissedSetsStatusToDismissedNotResolved() {
        UUID reportId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        Report report = Report.file(reporterId, ReportEntityType.USER, entityId, ReportReason.SPAM, null, ReportPriority.ROUTINE);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        var response = reportService.resolve(moderatorId, reportId, ReportResolutionAction.DISMISSED, "no evidence found");

        assertThat(response.status()).isEqualTo("DISMISSED");
        assertThat(response.resolutionAction()).isEqualTo("DISMISSED");
    }

    @Test
    void resolveWithARealActionSetsStatusToResolved() {
        UUID reportId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        Report report = Report.file(reporterId, ReportEntityType.USER, entityId, ReportReason.HARASSMENT, null, ReportPriority.SAFETY);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        var response = reportService.resolve(moderatorId, reportId, ReportResolutionAction.RESTRICTED, "substantiated");

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolutionAction()).isEqualTo("RESTRICTED");
    }
}
