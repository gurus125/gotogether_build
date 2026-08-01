package com.gotogether.admin.dto;

/**
 * {@code GET /admin/dashboard} (API Spec Section 16, Purpose: "KPI overview,
 * Business Rules Operations Module D"). Module D ("Analytics &amp; Reporting")
 * is Phase 9's {@code analytics} module scope (confirmed during this phase's
 * docs review — Operations doc page 5 places it after Module C, and no
 * {@code analytics_events} ingestion/aggregation pipeline exists yet even
 * though the table itself does). Rather than fabricate revenue/growth/
 * engagement metrics this pass has no real pipeline for, this dashboard is
 * scoped down to open-queue counts only — genuinely available data across
 * the modules this phase actually built, matching the same "counts, not a
 * full KPI set" scoping already used by every other deferred-analytics note
 * in this codebase. The full metric set belongs to {@code GET
 * /admin/analytics}, which this pass does not implement at all (see {@code
 * AdminController}'s class doc).
 */
public record AdminDashboardResponse(
        long openReportsCount,
        long emergencyReportsCount,
        long safetyReportsCount,
        long pendingUserVerificationsCount,
        long pendingCompanyVerificationsCount) {
}
