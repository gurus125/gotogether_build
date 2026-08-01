package com.gotogether.trip.entity;

import com.gotogether.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trip gallery storage with explicit ordering and one primary (DB Schema
 * Part 1) — {@code trip_id} is a plain UUID, not a JPA relation to {@link
 * Trip}, even though both are owned by this same module; kept consistent
 * with the rest of the codebase's preference for explicit id-based queries
 * over bidirectional entity graphs (no {@code @OneToMany} on {@code Trip}).
 * The DB's {@code ux_trip_images_one_primary_per_trip} partial unique index
 * is the actual backstop for "exactly one primary image" — {@code
 * TripService} only needs to unset the previous primary before setting a new one.
 */
@Entity
@Table(name = "trip_images")
public class TripImage extends BaseEntity {

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TripImage() {
        // JPA
    }

    public static TripImage of(UUID tripId, String imageUrl, short displayOrder, boolean primary) {
        TripImage image = new TripImage();
        image.tripId = tripId;
        image.imageUrl = imageUrl;
        image.displayOrder = displayOrder;
        image.primary = primary;
        return image;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(short displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
