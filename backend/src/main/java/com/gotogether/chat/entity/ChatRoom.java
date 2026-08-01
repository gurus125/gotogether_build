package com.gotogether.chat.entity;

import com.gotogether.common.entity.BaseEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * One row per Trip Chat at MVP (DB Schema Part 2) — {@code type=DIRECT} rows
 * are schema-ready but never created by this module yet (see {@link
 * ChatRoomType}'s doc). Extends {@link BaseEntity} directly, not {@code
 * AuditableEntity} — this table has no {@code updated_at} column (V3
 * migration) and isn't in V5's updated_at-trigger list, since {@code
 * is_archived}/{@code last_sequence_number} are its only mutable fields and
 * neither needs a generic "last modified" timestamp.
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom extends BaseEntity {

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "chat_room_type")
    private ChatRoomType type = ChatRoomType.TRIP;

    @Column(name = "trip_id", updatable = false)
    private UUID tripId;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    /** Owned entirely by {@code trg_messages_assign_sequence} (V5 migration) — Hibernate never writes this, only reads it back. */
    @Column(name = "last_sequence_number", insertable = false, updatable = false)
    private long lastSequenceNumber;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ChatRoom() {
        // JPA
    }

    public static ChatRoom forTrip(UUID tripId) {
        ChatRoom room = new ChatRoom();
        room.type = ChatRoomType.TRIP;
        room.tripId = tripId;
        return room;
    }

    public ChatRoomType getType() {
        return type;
    }

    public UUID getTripId() {
        return tripId;
    }

    public boolean isArchived() {
        return archived;
    }

    public long getLastSequenceNumber() {
        return lastSequenceNumber;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** {@code Active -> Archived} (Chapter 3 Section 3.5): Trip reaches Completed or Cancelled. Idempotent. */
    public void archive() {
        this.archived = true;
    }
}
