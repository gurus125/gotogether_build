package com.gotogether.trip.entity;

import com.gotogether.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Bookmarking join table, pure N:N with no lifecycle of its own (DB Schema Part 1). */
@Entity
@Table(name = "saved_trips")
public class SavedTrip extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SavedTrip() {
        // JPA
    }

    public static SavedTrip of(UUID userId, UUID tripId) {
        SavedTrip saved = new SavedTrip();
        saved.userId = userId;
        saved.tripId = tripId;
        return saved;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
