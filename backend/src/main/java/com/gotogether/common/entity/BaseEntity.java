package com.gotogether.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Base for every JPA entity in the schema.
 *
 * <p>Primary keys are UUID, generated application-side via Hibernate's
 * {@link UuidGenerator} — this is one of the two PK strategies the DB Schema
 * doc explicitly allows ("generated app-side or via gen_random_uuid()"), and
 * app-side generation is preferred here so a new entity's id is known before
 * the INSERT round-trip (useful for building related rows in the same
 * transaction, e.g. a Trip and its Chat Room). The column also carries a
 * {@code gen_random_uuid()} DEFAULT at the DB level (see V1 migration) as a
 * defense-in-depth backstop for any direct-SQL path that bypasses JPA.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    public UUID getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
