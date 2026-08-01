package com.gotogether.chat.controller;

import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.chat.dto.ChatRoomSummary;
import com.gotogether.chat.dto.MarkReadRequest;
import com.gotogether.chat.dto.MessageResponse;
import com.gotogether.chat.dto.MuteRequest;
import com.gotogether.chat.dto.PinMessageRequest;
import com.gotogether.chat.dto.SendMessageRequest;
import com.gotogether.chat.service.ChatService;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat APIs (API Specification Section 10) — core Trip Chat only this pass;
 * see {@code ChatService}'s class doc for the full list of deferred pieces
 * (Direct Messages, attachments, Polls/Expense, mentions, message editing).
 * {@link #send} also fans out a {@code chat_message} notification to every
 * other room participant (Phase 6) — a targeted {@code CHAT_MENTION}
 * variant isn't wired since @mention parsing itself was deferred.
 */
@RestController
public class ChatController {

    private final ChatService chatService;
    private final MembershipService membershipService;
    private final NotificationService notificationService;

    public ChatController(ChatService chatService, MembershipService membershipService, NotificationService notificationService) {
        this.chatService = chatService;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
    }

    @GetMapping("/users/me/chat-rooms")
    public CursorPageResponse<ChatRoomSummary> chatRooms(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return withLiveMemberCounts(chatService.listChatRooms(principal.userId(), cursor, limit));
    }

    @GetMapping("/chat-rooms/{id}/messages")
    public CursorPageResponse<MessageResponse> messages(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(defaultValue = "20") int limit) {
        return chatService.getMessages(principal.userId(), id, beforeSequence, limit);
    }

    @PostMapping("/chat-rooms/{id}/messages")
    public ResponseEntity<MessageResponse> send(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        MessageResponse response = chatService.sendMessage(principal.userId(), id, request);
        notifyOtherParticipants(id, principal.userId(), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * {@code chat_message} notification fan-out (Chapter 1 §18's "New
     * message" card) — every other participant, not just an @mention target
     * (mentions aren't parsed yet). {@code entityType}/{@code entityId} point
     * at the trip ({@link ChatService#getTripId}), not the message itself —
     * {@code TripChatScreen} (mobile) opens by trip id and resolves its own
     * room from there, so a message id can't be deep-linked to anything a
     * screen actually takes as a parameter.
     */
    private void notifyOtherParticipants(UUID chatRoomId, UUID senderId, MessageResponse message) {
        String preview = message.body() == null ? "" : (message.body().length() > 80 ? message.body().substring(0, 80) + "…" : message.body());
        String senderName = message.senderDisplayName() != null ? message.senderDisplayName() : "Someone";
        UUID tripId = chatService.getTripId(chatRoomId);
        for (UUID recipientId : chatService.getOtherParticipantIds(chatRoomId, senderId)) {
            notificationService.create(
                    recipientId, senderId, "CHAT_MESSAGE", ReferencedEntityType.TRIPS.tableName(), tripId,
                    "New message", senderName + ": " + preview, "low");
        }
    }

    @PatchMapping("/messages/{id}/pin")
    public MessageResponse pin(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody PinMessageRequest request) {
        return chatService.pinMessage(principal.userId(), id, request.pinCategory());
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        chatService.deleteMessage(principal.userId(), principal.role(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chat-rooms/{id}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody MarkReadRequest request) {
        chatService.markRead(principal.userId(), id, request.upToMessageId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chat-rooms/{id}/mute")
    public ResponseEntity<Void> mute(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody MuteRequest request) {
        chatService.setMuted(principal.userId(), id, request.isMuted());
        return ResponseEntity.noContent().build();
    }

    /**
     * Same controller-layer composition pattern as {@code
     * TripController.withLiveCounts} — see that class's doc. This keeps
     * {@code ChatService} independent of {@code MembershipService} (avoiding
     * a cycle, since {@code MembershipService} already depends on {@code
     * ChatService} — see {@code ChatService}'s class doc).
     */
    private CursorPageResponse<ChatRoomSummary> withLiveMemberCounts(CursorPageResponse<ChatRoomSummary> page) {
        if (page.items().isEmpty()) {
            return page;
        }
        Map<UUID, Integer> counts = membershipService.countActiveMembersByTripIds(page.items().stream().map(ChatRoomSummary::tripId).toList());
        List<ChatRoomSummary> updated = page.items().stream().map(c -> c.withMemberCount(counts.getOrDefault(c.tripId(), c.memberCount()))).toList();
        return new CursorPageResponse<>(updated, page.nextCursor(), page.hasMore());
    }
}
