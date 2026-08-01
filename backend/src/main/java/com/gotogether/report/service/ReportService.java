package com.gotogether.report.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.RateLimitedException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.report.dto.CreateReportRequest;
import com.gotogether.report.dto.ReportEvidenceResponse;
import com.gotogether.report.dto.ReportResponse;
import com.gotogether.report.entity.Report;
import com.gotogether.report.entity.ReportEntityType;
import com.gotogether.report.entity.ReportEvidence;
import com.gotogether.report.entity.ReportPriority;
import com.gotogether.report.entity.ReportReason;
import com.gotogether.report.entity.ReportResolutionAction;
import com.gotogether.report.entity.ReportStatus;
import com.gotogether.report.repository.ReportEvidenceRepository;
import com.gotogether.report.repository.ReportRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The report module's only entry point for other modules (see {@code
 * UserService}'s doc for the pattern) — deliberately depends on nothing
 * else in this codebase. It is the one genuinely "leaf" module besides
 * {@code common}: filing/queuing/resolving a report only ever needs the
 * report itself plus the caller's own id, never another module's data. The
 * <em>consequences</em> of a resolution (restricting a user, hiding a trip,
 * freezing a Trust Score) are deliberately not this module's job — {@code
 * admin.service.AdminService} composes this module with every affected one
 * at the point of resolving a report, exactly the same "composition lives in
 * whichever module can safely depend on both sides" convention already used
 * throughout this codebase (see {@code TripService}'s dependency on {@code
 * company}, or {@code ReviewService}'s class doc for why the reverse
 * direction would cycle). Resolution here only updates the {@code reports}
 * row itself: status, resolution_action, resolution notes, resolved_at.
 *
 * <p><b>Scoped down for this pass:</b> entity existence is not verified
 * against the other four modules' data (i.e. filing a report against a
 * {@code trip}/{@code review}/{@code company} id that doesn't actually exist
 * currently succeeds) — API Specification Section 15 only documents a 422
 * {@code INVALID_ENTITY_TYPE} error (a bad <em>type</em> string), not a
 * not-found check on the id itself, and building five separate
 * cross-module existence checks (one per {@link ReportEntityType}) for a
 * table whose only real consumer is a trusted Moderator queue is more
 * surface area than the documented contract asks for. Flagged here rather
 * than silently assumed.
 */
@Service
public class ReportService {

    /** Business Rules Module B: "Report... rate-limited" — no specific number/window is given anywhere in the doc set, so this mirrors the OTP-request rate limit's own "a reasonable default, not formally specified" precedent (see {@code auth.service}'s {@code OtpService}). */
    private static final int RATE_LIMIT_MAX_REPORTS = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int DEFAULT_LIMIT = 20;

    private final ReportRepository reportRepository;
    private final ReportEvidenceRepository reportEvidenceRepository;

    public ReportService(ReportRepository reportRepository, ReportEvidenceRepository reportEvidenceRepository) {
        this.reportRepository = reportRepository;
        this.reportEvidenceRepository = reportEvidenceRepository;
    }

    /** {@code POST /reports} (API Spec Section 15) — always {@link ReportPriority#ROUTINE}; see {@link #fileEmergency} for the priority-escalated path. */
    @Transactional
    public ReportResponse file(UUID reporterId, CreateReportRequest request) {
        return toResponse(fileInternal(reporterId, request, ReportPriority.ROUTINE));
    }

    /** {@code POST /reports/emergency} (API Spec Section 15) — Business Rules Module B: "priority-flagged queue, distinct SLA" for genuinely unsafe situations; forces {@link ReportPriority#EMERGENCY} regardless of any priority the caller might try to pass (this endpoint's request shape has no priority field precisely so it can't be spoofed down). */
    @Transactional
    public ReportResponse fileEmergency(UUID reporterId, CreateReportRequest request) {
        return toResponse(fileInternal(reporterId, request, ReportPriority.EMERGENCY));
    }

    private Report fileInternal(UUID reporterId, CreateReportRequest request, ReportPriority priority) {
        long recentCount = reportRepository.countByReporterIdAndCreatedAtAfter(reporterId, OffsetDateTime.now().minus(RATE_LIMIT_WINDOW));
        if (recentCount >= RATE_LIMIT_MAX_REPORTS) {
            throw new RateLimitedException("REPORT_RATE_LIMITED: too many reports filed recently — try again later.");
        }

        ReportEntityType entityType = parseEntityType(request.entityType());
        ReportReason reason = parseReason(request.reason());

        Report report = Report.file(reporterId, entityType, request.entityId(), reason, request.details(), priority);
        return reportRepository.save(report);
    }

    /** {@code POST /reports/{id}/evidence} (API Spec Section 15) — owner-only (the reporter, not any other caller). */
    @Transactional
    public ReportEvidenceResponse addEvidence(UUID callerId, UUID reportId, String storageKey, String mimeType, Integer fileSizeBytes) {
        Report report = getReportOrThrow(reportId);
        if (!report.getReporterId().equals(callerId)) {
            throw new ForbiddenException("Only the reporter who filed this report can attach evidence to it.");
        }
        ReportEvidence evidence = reportEvidenceRepository.save(
                ReportEvidence.attach(reportId, storageKey, mimeType, fileSizeBytes, callerId));
        return toEvidenceResponse(evidence);
    }

    // --- admin-facing entry points (called by admin.service.AdminService — see this class's doc) ---

    /** {@code GET /admin/reports} (API Spec Section 16) — the Moderator triage queue. */
    public CursorPageResponse<ReportResponse> getQueue(ReportStatus status, ReportPriority priority, UUID assignedModeratorId, String cursor, int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        int offset = OffsetCursor.decode(cursor);
        var page = reportRepository.findQueue(status, priority, assignedModeratorId,
                PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit));
        List<ReportResponse> items = page.getContent().stream().map(this::toResponse).toList();
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    /** Read-only lookup used by {@code AdminService} to inspect a report's {@code entityType}/{@code entityId} before dispatching the enforcement side effect. */
    public ReportResponse get(UUID reportId) {
        return toResponse(getReportOrThrow(reportId));
    }

    /** {@code GET /admin/dashboard}'s open-report counts — see {@code AdminDashboardResponse}'s doc for why this dashboard is scoped down to counts only. */
    public DashboardCounts getDashboardCounts() {
        return new DashboardCounts(
                reportRepository.countByStatus(ReportStatus.OPEN) + reportRepository.countByStatus(ReportStatus.IN_REVIEW),
                reportRepository.countByStatusAndPriority(ReportStatus.OPEN, ReportPriority.EMERGENCY)
                        + reportRepository.countByStatusAndPriority(ReportStatus.IN_REVIEW, ReportPriority.EMERGENCY),
                reportRepository.countByStatusAndPriority(ReportStatus.OPEN, ReportPriority.SAFETY)
                        + reportRepository.countByStatusAndPriority(ReportStatus.IN_REVIEW, ReportPriority.SAFETY));
    }

    public record DashboardCounts(long openTotal, long emergency, long safety) {}

    /**
     * {@code POST /admin/reports/{id}/resolve}'s report-row half (API Spec
     * Section 16) — updates status/resolution/resolution_action only.
     * {@code AdminService} calls this <em>and</em> dispatches the actual
     * enforcement action (restrict/suspend/hide/freeze) in the same
     * transaction — see this class's doc for why that composition doesn't
     * live here.
     */
    @Transactional
    public ReportResponse resolve(UUID reviewerId, UUID reportId, ReportResolutionAction action, String resolutionNotes) {
        Report report = getReportOrThrow(reportId);
        if (!report.isOpenOrInReview()) {
            throw new ConflictException("This report has already been resolved.");
        }
        report.resolve(reviewerId, action, resolutionNotes);
        return toResponse(reportRepository.save(report));
    }

    // --- internal ---------------------------------------------------------------

    private ReportEntityType parseEntityType(String raw) {
        try {
            return ReportEntityType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnprocessableEntityException("INVALID_ENTITY_TYPE: '" + raw + "' is not a reportable entity type.");
        }
    }

    private ReportReason parseReason(String raw) {
        try {
            return ReportReason.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnprocessableEntityException("'" + raw + "' is not a recognised report reason.");
        }
    }

    private Report getReportOrThrow(UUID reportId) {
        return reportRepository.findById(reportId).orElseThrow(() -> ResourceNotFoundException.of("Report", reportId));
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(
                r.getId(), r.getReporterId(), r.getEntityType().name(), r.getEntityId(), r.getReason().name(),
                r.getDetails(), r.getStatus().name(), r.getPriority().name(), r.getAssignedModeratorId(),
                r.getResolution(), r.getResolutionAction() == null ? null : r.getResolutionAction().name(),
                r.getCreatedAt(), r.getResolvedAt());
    }

    private ReportEvidenceResponse toEvidenceResponse(ReportEvidence e) {
        return new ReportEvidenceResponse(e.getId(), e.getReportId(), e.getStorageKey(), e.getMimeType(), e.getFileSizeBytes(), e.getCreatedAt());
    }
}
