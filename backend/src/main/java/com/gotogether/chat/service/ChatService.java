package com.gotogether.chat.service;

import com.gotogether.chat.dto.ChatRoomSummary;
import com.gotogether.chat.dto.MessageResponse;
import com.gotogether.chat.dto.SendMessageRequest;
import com.gotogether.chat.entity.ChatParticipant;
import com.gotogether.chat.entity.ChatRoom;
import com.gotogether.chat.entity.Message;
import com.gotogether.chat.entity.MessageType;
import com.gotogether.chat.repository.ChatParticipantRepository;
import com.gotogether.chat.repository.ChatRoomRepository;
import com.gotogether.chat.repository.MessageRepository;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.service.TripService;
import com.gotogether.user.entity.AccountRole;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chat module's only entry point for other modules — everything else
 * ({@code chat_rooms}/{@code messages}/{@code chat_participants} entities and
 * repositories) is package-private to this module in practice (enforced by
 * {@code ArchitectureTest}).
 *
 * <p>Depends one-directionally on {@code trip} (via {@link TripService}, to
 * resolve/lazily-seat the organizer and read trip cards for the Chat List)
 * and {@code profile} (sender display names) — never the reverse. {@link
 * #unlockForUser} is called directly from {@code JoinRequestService.accept}
 * and {@code JoinRequestService.promoteWaitlistIfCapacityAvailable} (both
 * already depend on {@code trip} and {@code membership}, so {@code
 * joinrequest} -> {@code chat} -> {@code trip} introduces no cycle), and
 * {@link #archiveForTrip} is called directly from {@code
 * MembershipService.completeTrip} for the same reason ({@code membership}
 * already depends on {@code trip}). {@code trip} -> {@code chat} would cycle
 * back through this class's own {@code trip} dependency, which is exactly
 * why the Trip-Cancellation-archives-Chat rule (Business Rules Cross-Module
 * Rules table) is wired at the controller layer ({@code TripController.cancel})
 * instead of inside {@code TripService} — see that controller's doc. {@link
 * #ensureRoomExists} is wired the same way, from {@code TripController.publish}.
 *
 * <p><b>Scoped down for this pass</b> (flagged here rather than silently
 * dropped — full scope confirmed by Business Rules Module C, deferred by
 * explicit user decision before Phase 4 started): only {@link
 * MessageType#TEXT} messages; no Direct Messages ({@code
 * com.gotogether.chat.entity.ChatRoomType#DIRECT} rooms); no attachments
 * (image/voice/document/location — needs a {@code StorageService} that
 * doesn't exist yet, same gap as trip/profile image upload); no Polls or
 * Expense messages (both need dedicated business logic beyond a plain
 * message row, even though Business Rules Module C explicitly confirms Polls
 * as in-scope); no {@code @mention} targeted notifications (the {@code
 * notification} module is Phase 6); no message editing (the DB has {@code
 * is_edited}/{@code edited_at} columns and API Spec Section 17 has a {@code
 * message_edit_window_minutes} constant, but Section 10's endpoint table only
 * ever specifies a delete endpoint, never a PATCH-body-edit one — a real,
 * unflagged documentation gap, not something silently invented here). This
 * module also doesn't separately track Chapter 3 Section 3.5's Unlocked-vs-
 * Active distinction — both permit posting identically here, so the only
 * observable difference (an empty-state "welcome card" vs. a live thread) is
 * a Flutter-side decision based on whether any messages exist yet.
 */
@Service
public class ChatService {

    /** Business Rules Module C: "a sender may delete their own message for everyone within 10 minutes of sending." Flagged there as a recommended-configurable constant; hardcoded here since {@code GET /system/config} doesn't exist in this codebase yet (same rationale as {@code JoinRequestService}'s SLA_WINDOW constant). */
    private static final int SELF_DELETE_WINDOW_MINUTES = 10;

    /** Validation Rules (API Spec Section 20): "no hard length cap specified, recommend 2000 chars as an implementation default." */
    private static final int MAX_MESSAGE_BODY_LENGTH = 2000;

    private static final int DEFAULT_LIMIT = 20;

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final TripService tripService;
    private final ProfileService profileService;
    private final EntityManager entityManager;

    public ChatService(
            ChatRoomRepository chatRoomRepository, MessageRepository messageRepository,
            ChatParticipantRepository chatParticipantRepository, TripService tripService, ProfileService profileService,
            EntityManager entityManager) {
        this.chatRoomRepository = chatRoomRepository;
        this.messageRepository = messageRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.tripService = tripService;
        this.profileService = profileService;
        this.entityManager = entityManager;
    }

    // --- cross-module entry points -------------------------------------------

    /**
     * Chapter 3 Section 3.5: {@code Locked -> Unlocked}, the instant a Join
     * Request reaches Accepted. Called directly from {@code
     * JoinRequestService} — see this class's doc for why that direct
     * dependency is safe. Idempotent (safe to call for a user who's already a
     * participant, e.g. a re-accepted-after-cooldown Member per Business
     * Rules Module C's "regains full chat history rather than starting a
     * fresh thread" edge case — there's nothing to redo since the row already
     * exists).
     */
    @Transactional
    public void unlockForUser(UUID tripId, UUID userId) {
        ChatRoom room = ensureRoomAndOrganizerSeat(tripId);
        if (!chatParticipantRepository.existsByChatRoomIdAndUserId(room.getId(), userId)) {
            chatParticipantRepository.save(ChatParticipant.create(room.getId(), userId));
        }
    }

    /**
     * Creates the Trip Chat room and seats the Organizer the moment a trip is
     * Published, rather than waiting for the first Accepted Join Request —
     * Chapter 3 Section 3.5 only defines the applicant's own unlock trigger,
     * it doesn't say the Organizer must wait for someone else to join before
     * they can open their own trip's chat and pin a welcome/meeting-point
     * message. Called from {@code TripController.publish} (controller layer,
     * not {@code TripService} — same cycle-avoidance reasoning as {@code
     * TripController.cancel}, see this class's doc). Idempotent — a no-op if
     * a Join Request was somehow already accepted first (Verified Partner
     * trips effectively auto-accept on payment, which could call {@link
     * #unlockForUser} before this ever runs).
     */
    @Transactional
    public void ensureRoomExists(UUID tripId) {
        ensureRoomAndOrganizerSeat(tripId);
    }

    /**
     * Chapter 3 Section 3.5 / Business Rules Cross-Module Rules: {@code
     * Active -> Archived} the instant a trip reaches Completed or Cancelled.
     * Called directly from {@code MembershipService.completeTrip}, and from
     * {@code TripController.cancel} at the controller layer (not from {@code
     * TripService} itself) — see this class's doc. A no-op if the trip never
     * got far enough to have a chat room at all (e.g. cancelled while still
     * Draft/Published with zero Accepted members).
     */
    @Transactional
    public void archiveForTrip(UUID tripId) {
        chatRoomRepository.findByTripId(tripId).ifPresent(room -> {
            if (!room.isArchived()) {
                room.archive();
                chatRoomRepository.save(room);
            }
        });
    }

    /**
     * The trip a chat room belongs to — used by {@code ChatController} to
     * fan out {@code chat_message} notifications (Phase 6) without needing
     * its own {@code ChatRoom} lookup.
     */
    public UUID getTripId(UUID chatRoomId) {
        return chatRoomRepository.findById(chatRoomId).orElseThrow(() -> ResourceNotFoundException.of("Chat room", chatRoomId)).getTripId();
    }

    /** Every participant's user id except {@code excludingUserId} — the {@code chat_message} notification fan-out's recipient list (Chapter 1 §18). */
    public List<UUID> getOtherParticipantIds(UUID chatRoomId, UUID excludingUserId) {
        return chatParticipantRepository.findByChatRoomId(chatRoomId).stream()
                .map(ChatParticipant::getUserId)
                .filter(id -> !id.equals(excludingUserId))
                .toList();
    }

    // --- reads ----------------------------------------------------------------

    /** {@code GET /users/me/chat-rooms} (API Spec Section 10) — Trip Groups only this pass (no Direct Messages yet), most-recent-activity-first. */
    public CursorPageResponse<ChatRoomSummary> listChatRooms(UUID userId, String cursor, int limit) {
        List<ChatParticipant> participations = chatParticipantRepository.findByUserId(userId);
        int offset = OffsetCursor.decode(cursor);
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;

        List<ChatRoomSummary> summaries = participations.stream()
                .map(p -> toSummary(p, chatRoomRepository.findById(p.getChatRoomId()).orElse(null)))
                .filter(Objects::nonNull)
                .sorted((a, b) -> {
                    OffsetDateTime aTime = a.lastMessageAt();
                    OffsetDateTime bTime = b.lastMessageAt();
                    if (aTime == null && bTime == null) return 0;
                    if (aTime == null) return 1;
                    if (bTime == null) return -1;
                    return bTime.compareTo(aTime);
                })
                .toList();

        int end = Math.min(offset + effectiveLimit, summaries.size());
        List<ChatRoomSummary> page = offset >= summaries.size() ? List.of() : summaries.subList(offset, end);
        String nextCursor = end < summaries.size() ? OffsetCursor.encode(end) : null;
        return CursorPageResponse.of(page, nextCursor);
    }

    /** {@code GET /chat-rooms/{id}/messages} (API Spec Section 10) — reverse-chronological, keyed on {@code sequence_number} per the DB Schema Part 2 improvement the spec calls out, rather than {@code CursorPageResponse}'s usual offset encoding. */
    public CursorPageResponse<MessageResponse> getMessages(UUID userId, UUID chatRoomId, Long beforeSequence, int limit) {
        getParticipantOrThrow(chatRoomId, userId);
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        PageRequest pageRequest = PageRequest.of(0, effectiveLimit + 1);

        List<Message> messages = beforeSequence != null
                ? messageRepository.findByChatRoomIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(chatRoomId, beforeSequence, pageRequest)
                : messageRepository.findByChatRoomIdOrderBySequenceNumberDesc(chatRoomId, pageRequest);

        boolean hasMore = messages.size() > effectiveLimit;
        List<Message> page = hasMore ? messages.subList(0, effectiveLimit) : messages;
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getSequenceNumber()) : null;
        List<MessageResponse> items = page.stream().map(this::toMessageResponse).toList();
        return CursorPageResponse.of(items, nextCursor);
    }

    // --- writes -----------------------------------------------------------------

    @Transactional
    public MessageResponse sendMessage(UUID userId, UUID chatRoomId, SendMessageRequest request) {
        getParticipantOrThrow(chatRoomId, userId);
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> ResourceNotFoundException.of("Chat room", chatRoomId));
        if (room.isArchived()) {
            throw new ConflictException("This trip's chat is archived and no longer accepts new messages.");
        }

        MessageType type;
        try {
            type = MessageType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnprocessableEntityException("Unknown message type: " + request.type());
        }
        if (type != MessageType.TEXT) {
            throw new UnprocessableEntityException(
                    "Only text messages are supported right now — " + type.name().toLowerCase() + " messages are coming in a future update.");
        }
        if (request.body() == null || request.body().isBlank()) {
            throw new UnprocessableEntityException("A message needs a body.");
        }
        if (request.body().length() > MAX_MESSAGE_BODY_LENGTH) {
            throw new UnprocessableEntityException("Messages must be " + MAX_MESSAGE_BODY_LENGTH + " characters or fewer.");
        }

        Message message = Message.text(chatRoomId, userId, request.body(), request.replyToMessageId());
        message = messageRepository.saveAndFlush(message);
        // sequence_number/created_at are trigger/DB-assigned (see Message's doc). A plain findById()
        // here would NOT re-read them: within this same transaction, Hibernate's persistence context
        // (first-level cache) returns this exact managed instance by identity instead of issuing a
        // fresh SELECT, so the in-memory object would still show the pre-insert values (null
        // createdAt, 0 sequenceNumber) despite the DB row having the real trigger-assigned ones.
        // entityManager.refresh() forces an actual re-read of the row Hibernate just inserted.
        entityManager.refresh(message);
        return toMessageResponse(message);
    }

    @Transactional
    public MessageResponse pinMessage(UUID userId, UUID messageId, String pinCategory) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        ChatRoom room = chatRoomRepository.findById(message.getChatRoomId())
                .orElseThrow(() -> ResourceNotFoundException.of("Chat room", message.getChatRoomId()));
        requireOrganizer(room, userId);
        message.setPin(pinCategory);
        return toMessageResponse(messageRepository.save(message));
    }

    @Transactional
    public void deleteMessage(UUID userId, AccountRole actingRole, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        if (message.isDeleted()) {
            throw new ConflictException("This message has already been deleted.");
        }
        boolean isModerator = actingRole == AccountRole.MODERATOR || actingRole == AccountRole.ADMIN;
        if (!isModerator) {
            if (message.getSenderId() == null || !message.getSenderId().equals(userId)) {
                throw new ForbiddenException("You can only delete your own messages.");
            }
            if (message.getCreatedAt() == null || message.getCreatedAt().plusMinutes(SELF_DELETE_WINDOW_MINUTES).isBefore(OffsetDateTime.now())) {
                throw new ConflictException("The 10-minute window to delete this message has passed — ask a moderator if it needs to come down.");
            }
        }
        message.softDelete(userId);
        messageRepository.save(message);
    }

    @Transactional
    public void markRead(UUID userId, UUID chatRoomId, UUID upToMessageId) {
        ChatParticipant participant = getParticipantOrThrow(chatRoomId, userId);
        participant.markRead(upToMessageId);
        chatParticipantRepository.save(participant);
    }

    @Transactional
    public void setMuted(UUID userId, UUID chatRoomId, boolean isMuted) {
        ChatParticipant participant = getParticipantOrThrow(chatRoomId, userId);
        participant.setMuted(isMuted);
        chatParticipantRepository.save(participant);
    }

    // --- internal helpers ---------------------------------------------------

    /**
     * Lazily creates the Trip's Chat Room and the Organizer's own participant
     * seat the first time either is touched — trip creation happens entirely
     * inside {@code trip} module (before {@code chat} existed as a concept it
     * could react to), so there's no single "the trip was just published"
     * hook this module can react to without creating the exact circular
     * dependency this class's doc explains avoiding. Idempotent, mirroring
     * {@code MembershipService.ensureOrganizerSeat}'s identical pattern.
     */
    private ChatRoom ensureRoomAndOrganizerSeat(UUID tripId) {
        ChatRoom room = chatRoomRepository.findByTripId(tripId)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.forTrip(tripId)));
        TripCapacityInfo info = tripService.getCapacityInfo(tripId);
        if (!chatParticipantRepository.existsByChatRoomIdAndUserId(room.getId(), info.organizerId())) {
            chatParticipantRepository.save(ChatParticipant.create(room.getId(), info.organizerId()));
        }
        return room;
    }

    private ChatParticipant getParticipantOrThrow(UUID chatRoomId, UUID userId) {
        return chatParticipantRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a participant in this chat."));
    }

    private void requireOrganizer(ChatRoom room, UUID userId) {
        if (room.getTripId() == null || !tripService.getCapacityInfo(room.getTripId()).organizerId().equals(userId)) {
            throw new ForbiddenException("Only the organizer can pin messages.");
        }
    }

    private ChatRoomSummary toSummary(ChatParticipant participant, ChatRoom room) {
        if (room == null || room.getTripId() == null) {
            return null;
        }
        TripSummary trip = tripService.getSummary(room.getTripId());
        Message lastMessage = messageRepository.findFirstByChatRoomIdOrderBySequenceNumberDesc(room.getId()).orElse(null);

        String preview = null;
        String senderName = null;
        OffsetDateTime lastMessageAt = null;
        if (lastMessage != null) {
            lastMessageAt = lastMessage.getCreatedAt();
            preview = lastMessage.isDeleted() ? "Message deleted" : lastMessage.getBody();
            if (lastMessage.getSenderId() != null) {
                senderName = profileService.getPublicSummary(lastMessage.getSenderId()).displayName();
            }
        }

        long lastReadSequence = 0L;
        if (participant.getLastReadMessageId() != null) {
            lastReadSequence = messageRepository.findById(participant.getLastReadMessageId())
                    .map(Message::getSequenceNumber).orElse(0L);
        }
        long unreadCount = messageRepository.countByChatRoomIdAndSequenceNumberGreaterThan(room.getId(), lastReadSequence);

        return new ChatRoomSummary(
                room.getId(), trip.id(), trip.title(), trip.kind().name(), trip.status().name(), trip.coverImageUrl(),
                trip.joinedCount(), preview, senderName, lastMessageAt, unreadCount, participant.isMuted(), room.isArchived());
    }

    private MessageResponse toMessageResponse(Message message) {
        String senderName = null;
        String senderPhoto = null;
        if (message.getSenderId() != null) {
            ProfilePublicSummary profile = profileService.getPublicSummary(message.getSenderId());
            senderName = profile.displayName();
            senderPhoto = profile.photoUrl();
        }
        boolean deleted = message.isDeleted();
        return new MessageResponse(
                message.getId(), message.getSequenceNumber(), message.getChatRoomId(), message.getSenderId(),
                senderName, senderPhoto, message.getType().name(), deleted ? null : message.getBody(),
                message.getReplyToMessageId(), message.isPinned(), message.getPinCategory(),
                message.isEdited(), deleted, message.getCreatedAt());
    }
}
