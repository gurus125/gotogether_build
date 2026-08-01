package com.gotogether.destination.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;

/**
 * The curated, platform-controlled destination list (DB Schema Part 1) —
 * deliberately not free text (Chapter 1 Section 14: "keeps matching data
 * clean"). Rows are seeded once via the V6 migration and are read-only from
 * the API's perspective at MVP; there is no create/update/delete endpoint for
 * destinations (Chapter 1 Section 9b: destination curation is a platform
 * responsibility, not a user-facing feature).
 *
 * <p>{@code category} follows the same native-Postgres-enum pattern as the
 * user module's enum fields — see {@link NativeEnumJdbcType}'s class doc for
 * why a hand-rolled {@code JdbcType} is used instead of a built-in Hibernate
 * mechanism.
 */
@Entity
@Table(name = "destinations")
public class Destination extends AuditableEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "category", nullable = false, columnDefinition = "destination_category")
    private DestinationCategory category;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "popularity_rank")
    private Integer popularityRank;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Destination() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public DestinationCategory getCategory() {
        return category;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public Integer getPopularityRank() {
        return popularityRank;
    }

    public boolean isActive() {
        return active;
    }
}
