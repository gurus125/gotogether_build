package com.gotogether.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /users/me/chat-rooms} row (API Spec Section 10) — one card in
 * the Chat List (Chapter 1 Section 18: Trip Groups above Direct Messages).
 * Only {@code TRIP} rooms are ever produced this pass — see {@code
 * ChatRoomType}'s doc.
 */
public record ChatRoomSummary(
        UUID chatRoomId,
        UUID tripId,
        String tripTitle,
        String tripKind,
        String tripStatus,
        String tripCoverImageUrl,
        int memberCount,
        String lastMessagePreview,
        String lastMessageSenderName,
        OffsetDateTime lastMessageAt,
        long unreadCount,
        boolean isMuted,
        boolean isArchived) {

    /**
     * Returns a copy with a real {@code memberCount} — used at the controller
     * layer ({@code ChatController}) to overlay a live count from {@code
     * MembershipService}, the same composition pattern as {@code
     * TripSummary#withJoinedCount} and for the identical reason: {@code
     * MembershipService} already depends on {@code ChatService} (to trigger
     * chat unlock/archive), so {@code ChatService} cannot also depend on
     * {@code MembershipService} without creating a cycle.
     */
    public ChatRoomSummary withMemberCount(int realMemberCount) {
        return new ChatRoomSummary(
                chatRoomId, tripId, tripTitle, tripKind, tripStatus, tripCoverImageUrl, realMemberCount,
                lastMessagePreview, lastMessageSenderName, lastMessageAt, unreadCount, isMuted, isArchived);
    }
}
