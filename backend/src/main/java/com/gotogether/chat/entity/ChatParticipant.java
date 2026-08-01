package com.gotogether.chat.entity;

import com.gotogether.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-user state within a Chat Room (DB Schema Part 2) — existence of this
 * row IS the Locked/Unlocked distinction from Chapter 3 Section 3.5 (no row
 * = Locked; a row existing means Unlocked-or-Active, since this module
 * doesn't separately track that split — see {@code ChatService}'s class
 * doc). {@code left_at} is unused this pass: chat access is derived strictly
 * from Trip Membership per Business Rules Module C ("no leave chat but stay
 * in trip state"), so a participant row is never soft-left on its own — only
 * the whole room is archived via {@link ChatRoom#archive}.
 */
@Entity
@Table(name = "chat_participants")
public class ChatParticipant extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false, updatable = false)
    private UUID chatRoomId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "is_muted", nullable = false)
    private boolean muted = false;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "last_read_at")
    private OffsetDateTime lastReadAt;

    protected ChatParticipant() {
        // JPA
    }

    public static ChatParticipant create(UUID chatRoomId, UUID userId) {
        ChatParticipant participant = new ChatParticipant();
        participant.chatRoomId = chatRoomId;
        participant.userId = userId;
        return participant;
    }

    public UUID getChatRoomId() {
        return chatRoomId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    public boolean isMuted() {
        return muted;
    }

    public UUID getLastReadMessageId() {
        return lastReadMessageId;
    }

    public OffsetDateTime getLastReadAt() {
        return lastReadAt;
    }

    /** Chapter 3 Section 3.5: {@code Active <-> Muted}, per-user, notification-only — never affects message delivery/history. */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public void markRead(UUID messageId) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = OffsetDateTime.now();
    }
}
