package com.gotogether.report.repository;

import com.gotogether.report.entity.Report;
import com.gotogether.report.entity.ReportPriority;
import com.gotogether.report.entity.ReportStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /** Rate-limiting input (DB Schema Part 3: "enforced at application layer via time-windowed count query, not a DB constraint" — see {@code ReportService#RATE_LIMIT_WINDOW}). */
    long countByReporterIdAndCreatedAtAfter(UUID reporterId, OffsetDateTime since);

    /** {@code GET /admin/dashboard}'s open-queue counts (Phase 8, scoped-down — see {@code AdminDashboardResponse}'s doc). */
    long countByStatus(ReportStatus status);

    long countByStatusAndPriority(ReportStatus status, ReportPriority priority);

    /**
     * {@code GET /admin/reports} (API Spec Section 16) — the Moderator triage
     * queue, ordered exactly per Operations Module B's Moderator Workflow:
     * emergency first, then safety, then routine, newest-first within each
     * tier. A {@code CASE}-based {@code ORDER BY} rather than relying on
     * {@link ReportPriority}'s enum ordinal, since JPQL sorts a converted
     * enum column alphabetically by its converted string, not by Java
     * declaration order.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:priority IS NULL OR r.priority = :priority)
              AND (:assignedModeratorId IS NULL OR r.assignedModeratorId = :assignedModeratorId)
            ORDER BY
              CASE r.priority WHEN com.gotogether.report.entity.ReportPriority.EMERGENCY THEN 0
                              WHEN com.gotogether.report.entity.ReportPriority.SAFETY THEN 1
                              ELSE 2 END,
              r.createdAt DESC
            """)
    Page<Report> findQueue(
            @Param("status") ReportStatus status, @Param("priority") ReportPriority priority,
            @Param("assignedModeratorId") UUID assignedModeratorId, Pageable pageable);
}
