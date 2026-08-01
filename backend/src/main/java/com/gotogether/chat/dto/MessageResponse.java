package com.gotogether.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mirrors {@code chat.entity.Message} (API Spec Section 10). {@code body} is
 * {@code null} once {@code isDeleted}, regardless of what's still in the DB
 * row — see {@code Message.softDelete}'s doc on why the underlying content is
 * preserved for a future Moderator audit view but never served through this
 * DTO.
 */
public record MessageResponse(
        UUID id,
        long sequenceNumber,
        UUID chatRoomId,
        UUID senderId,
        String senderDisplayName,
        String senderPhotoUrl,
        String type,
        String body,
        UUID replyToMessageId,
        boolean isPinned,
        String pinCategory,
        boolean isEdited,
        boolean isDeleted,
        OffsetDateTime createdAt) {
}
