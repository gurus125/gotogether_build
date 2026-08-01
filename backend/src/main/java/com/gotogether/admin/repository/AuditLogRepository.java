package com.gotogether.admin.repository;

import com.gotogether.admin.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * {@code GET /admin/audit-logs} (API Spec Section 16: {@code
     * ?actor_id&entity_type&cursor}) — {@code actorId} is required for a
     * Moderator (forced to self by {@code AdminService}), optional for an
     * Admin (null means "everyone's"); {@code entityType} is always
     * optional.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorId IS NULL OR a.actorId = :actorId)
              AND (:entityType IS NULL OR a.entityType = :entityType)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("actorId") UUID actorId, @Param("entityType") String entityType, Pageable pageable);
}
