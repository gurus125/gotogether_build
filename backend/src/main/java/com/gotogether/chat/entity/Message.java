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
 * A single Trip Chat message (DB Schema Part 2). {@code sequence_number} is
 * assigned entirely by {@code trg_messages_assign_sequence} (V5 migration, an
 * atomic per-room counter) — never set from Java, and re-read via a fresh
 * {@code findById} immediately after {@code save()} in {@code
 * ChatService.sendMessage} since Hibernate doesn't otherwise refresh
 * trigger-populated columns on the in-memory instance (same convention this
 * codebase already uses for {@code AuditableEntity}'s read-only {@code
 * createdAt}/{@code updatedAt}). Extends {@link BaseEntity} directly, not
 * {@code AuditableEntity} — this table has no {@code updated_at} column;
 * edits are tracked via the explicit {@code is_edited}/{@code edited_at} pair
 * instead (unused this pass — no message-edit endpoint exists in the API
 * Specification, only delete; see {@code ChatService}'s class doc).
 */
@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

    @Column(name = "sequence_number", insertable = false, updatable = false)
    private long sequenceNumber;

    @Column(name = "chat_room_id", nullable = false, updatable = false)
    private UUID chatRoomId;

    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "message_type")
    private MessageType type = MessageType.TEXT;

    @Column(name = "body")
    private String body;

    @Column(name = "reply_to_message_id", updatable = false)
    private UUID replyToMessageId;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "pin_category")
    private String pinCategory;

    @Column(name = "is_edited", nullable = false)
    private boolean edited = false;

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Message() {
        // JPA
    }

    public static Message text(UUID chatRoomId, UUID senderId, String body, UUID replyToMessageId) {
        Message message = new Message();
        message.chatRoomId = chatRoomId;
        message.senderId = senderId;
        message.type = MessageType.TEXT;
        message.body = body;
        message.replyToMessageId = replyToMessageId;
        return message;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public UUID getChatRoomId() {
        return chatRoomId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public MessageType getType() {
        return type;
    }

    public String getBody() {
        return body;
    }

    public UUID getReplyToMessageId() {
        return replyToMessageId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public String getPinCategory() {
        return pinCategory;
    }

    public boolean isEdited() {
        return edited;
    }

    public OffsetDateTime getEditedAt() {
        return editedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Organizer-only (Chapter 2 Section 2.4, confirmed by Business Rules Module C). {@code null} category clears the pin. */
    public void setPin(String pinCategory) {
        this.pinned = pinCategory != null;
        this.pinCategory = pinCategory;
    }

    /**
     * Self-delete within 10 minutes, or Moderator/Admin removal any time
     * (Business Rules Module C). Deliberately does NOT clear {@code body} —
     * Module C's doc requires "any deletion... still leaves an audit trail
     * visible to Moderators even if hidden from Members," so the raw row is
     * preserved; it's {@code ChatService}'s DTO mapping (not this entity)
     * that hides the body from the API response once {@link #isDeleted()}.
     */
    public void softDelete(UUID deletedBy) {
        this.deletedAt = OffsetDateTime.now();
        this.deletedBy = deletedBy;
        this.pinned = false;
        this.pinCategory = null;
    }
}
