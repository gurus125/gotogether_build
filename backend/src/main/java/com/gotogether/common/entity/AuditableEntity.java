package com.gotogether.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;

/**
 * Base for entities backed by a table with {@code created_at} / {@code
 * updated_at} columns (Part 1 Section 1: "every table gets created_at and
 * updated_at, defaulted and trigger-maintained").
 *
 * <p>Both columns are {@code insertable = false, updatable = false} — the DB
 * DEFAULT and the {@code trg_set_updated_at} trigger (V5 migration) fully own
 * these values. Hibernate never writes them; it only reads them back. This
 * keeps "when was this actually changed" trustworthy even for rows updated
 * by a direct-SQL admin/support script that never goes through the JPA layer.
 */
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
