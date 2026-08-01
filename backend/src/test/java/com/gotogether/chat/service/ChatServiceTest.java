package com.gotogether.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.chat.dto.SendMessageRequest;
import com.gotogether.chat.entity.ChatParticipant;
import com.gotogether.chat.entity.ChatRoom;
import com.gotogether.chat.entity.Message;
import com.gotogether.chat.repository.ChatParticipantRepository;
import com.gotogether.chat.repository.ChatRoomRepository;
import com.gotogether.chat.repository.MessageRepository;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.trip.dto.TripCapacityInfo;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.service.TripService;
import com.gotogether.user.entity.AccountRole;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private TripService tripService;
    @Mock private ProfileService profileService;
    @Mock private EntityManager entityManager;

    private ChatService chatService;

    private final UUID tripId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomRepository, messageRepository, chatParticipantRepository, tripService, profileService, entityManager);
        lenient().when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chatParticipantRepository.save(any(ChatParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private TripCapacityInfo capacityInfo() {
        return new TripCapacityInfo(tripId, organizerId, TripKind.COMMUNITY, TripStatus.ACCEPTING_REQUESTS, (short) 2, (short) 6);
    }

    private ChatRoom existingRoom(boolean archived) throws Exception {
        ChatRoom room = ChatRoom.forTrip(tripId);
        if (archived) {
            room.archive();
        }
        var idField = com.gotogether.common.entity.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(room, roomId);
        return room;
    }

    // --- unlockForUser --------------------------------------------------------

    @Test
    void unlockForUserCreatesTheRoomAndOrganizerSeatWhenNeitherExistsYet() {
        when(chatRoomRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo());
        when(chatParticipantRepository.existsByChatRoomIdAndUserId(any(), any())).thenReturn(false);

        chatService.unlockForUser(tripId, memberId);

        // one save for the organizer's own seat, one for the requested member's seat
        verify(chatParticipantRepository, times(2)).save(any(ChatParticipant.class));
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void unlockForUserIsIdempotentWhenTheParticipantRowAlreadyExists() throws Exception {
        ChatRoom room = existingRoom(false);
        when(chatRoomRepository.findByTripId(tripId)).thenReturn(Optional.of(room));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo());
        when(chatParticipantRepository.existsByChatRoomIdAndUserId(any(), any())).thenReturn(true);

        chatService.unlockForUser(tripId, memberId);

        verify(chatParticipantRepository, never()).save(any(ChatParticipant.class));
    }

    // --- ensureRoomExists (Publish-time seat, not just first-Accept) --------

    @Test
    void ensureRoomExistsCreatesTheRoomAndOrganizerSeatOnPublish() {
        when(chatRoomRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo());
        when(chatParticipantRepository.existsByChatRoomIdAndUserId(any(), any())).thenReturn(false);

        chatService.ensureRoomExists(tripId);

        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(chatParticipantRepository).save(any(ChatParticipant.class));
    }

    @Test
    void ensureRoomExistsIsANoOpWhenTheOrganizerSeatAlreadyExists() throws Exception {
        ChatRoom room = existingRoom(false);
        when(chatRoomRepository.findByTripId(tripId)).thenReturn(Optional.of(room));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo());
        when(chatParticipantRepository.existsByChatRoomIdAndUserId(any(), any())).thenReturn(true);

        chatService.ensureRoomExists(tripId);

        verify(chatParticipantRepository, never()).save(any());
        verify(chatRoomRepository, never()).save(any());
    }

    // --- archiveForTrip ---------------------------------------------------------

    @Test
    void archiveForTripArchivesAnExistingUnarchivedRoom() throws Exception {
        ChatRoom room = existingRoom(false);
        when(chatRoomRepository.findByTripId(tripId)).thenReturn(Optional.of(room));

        chatService.archiveForTrip(tripId);

        assertThat(room.isArchived()).isTrue();
        verify(chatRoomRepository).save(room);
    }

    @Test
    void archiveForTripIsANoOpWhenNoRoomExistsYet() {
        when(chatRoomRepository.findByTripId(tripId)).thenReturn(Optional.empty());

        chatService.archiveForTrip(tripId);

        verify(chatRoomRepository, never()).save(any());
    }

    // --- sendMessage ------------------------------------------------------------

    @Test
    void sendMessageThrowsWhenCallerIsNotAParticipant() {
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(memberId, roomId, new SendMessageRequest("TEXT", "hi", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessageThrowsWhenTheRoomIsArchived() throws Exception {
        ChatRoom room = existingRoom(true);
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(ChatParticipant.create(roomId, memberId)));
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> chatService.sendMessage(memberId, roomId, new SendMessageRequest("TEXT", "hi", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sendMessageRejectsNonTextTypesForNow() throws Exception {
        ChatRoom room = existingRoom(false);
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(ChatParticipant.create(roomId, memberId)));
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> chatService.sendMessage(memberId, roomId, new SendMessageRequest("POLL", "hi", null)))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void sendMessageRejectsABlankBody() throws Exception {
        ChatRoom room = existingRoom(false);
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(ChatParticipant.create(roomId, memberId)));
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> chatService.sendMessage(memberId, roomId, new SendMessageRequest("TEXT", "   ", null)))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void sendMessagePersistsAValidTextMessage() throws Exception {
        ChatRoom room = existingRoom(false);
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(ChatParticipant.create(roomId, memberId)));
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(profileService.getPublicSummary(memberId)).thenReturn(new ProfilePublicSummary(memberId, "Priya", null));
        // Mirrors what Postgres actually does on refresh(): stamps the trigger/DB-default fields
        // (sequence_number, created_at) that saveAndFlush() alone would leave unset on the entity.
        var sequenceField = Message.class.getDeclaredField("sequenceNumber");
        sequenceField.setAccessible(true);
        var createdAtField = Message.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        doAnswer(inv -> {
            Message m = inv.getArgument(0);
            sequenceField.set(m, 1L);
            createdAtField.set(m, OffsetDateTime.now());
            return null;
        }).when(entityManager).refresh(any(Message.class));

        var response = chatService.sendMessage(memberId, roomId, new SendMessageRequest("TEXT", "hello group", null));

        assertThat(response.body()).isEqualTo("hello group");
        assertThat(response.senderDisplayName()).isEqualTo("Priya");
        assertThat(response.isDeleted()).isFalse();
        assertThat(response.sequenceNumber()).isEqualTo(1L);
        assertThat(response.createdAt()).isNotNull();
    }

    // --- pinMessage ---------------------------------------------------------------

    @Test
    void pinMessageThrowsForNonOrganizer() throws Exception {
        ChatRoom room = existingRoom(false);
        Message message = Message.text(roomId, memberId, "meet at gate 3", null);
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo());

        assertThatThrownBy(() -> chatService.pinMessage(memberId, messageId, "meeting_point"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void pinMessageSucceedsForTheOrganizer() throws Exception {
        ChatRoom room = existingRoom(false);
        Message message = Message.text(roomId, memberId, "meet at gate 3", null);
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tripService.getCapacityInfo(tripId)).thenReturn(capacityInfo());
        when(profileService.getPublicSummary(memberId)).thenReturn(new ProfilePublicSummary(memberId, "Priya", null));

        var response = chatService.pinMessage(organizerId, messageId, "meeting_point");

        assertThat(response.isPinned()).isTrue();
        assertThat(response.pinCategory()).isEqualTo("meeting_point");
    }

    // --- deleteMessage ---------------------------------------------------------------

    @Test
    void deleteMessageAllowsSelfDeleteWithinTheTenMinuteWindow() throws Exception {
        Message message = Message.text(roomId, memberId, "oops typo", null);
        stampCreatedAt(message, OffsetDateTime.now().minusMinutes(2));
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        chatService.deleteMessage(memberId, AccountRole.INDIVIDUAL, messageId);

        assertThat(message.isDeleted()).isTrue();
    }

    @Test
    void deleteMessageRefusesSelfDeleteAfterTheTenMinuteWindow() throws Exception {
        Message message = Message.text(roomId, memberId, "oops typo", null);
        stampCreatedAt(message, OffsetDateTime.now().minusMinutes(15));
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.deleteMessage(memberId, AccountRole.INDIVIDUAL, messageId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteMessageRefusesANonSenderNonModerator() throws Exception {
        Message message = Message.text(roomId, memberId, "oops typo", null);
        stampCreatedAt(message, OffsetDateTime.now());
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.deleteMessage(organizerId, AccountRole.INDIVIDUAL, messageId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteMessageAllowsAModeratorRegardlessOfSenderOrWindow() throws Exception {
        Message message = Message.text(roomId, memberId, "policy violation", null);
        stampCreatedAt(message, OffsetDateTime.now().minusDays(1));
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        chatService.deleteMessage(UUID.randomUUID(), AccountRole.MODERATOR, messageId);

        assertThat(message.isDeleted()).isTrue();
    }

    @Test
    void deleteMessageRefusesDeletingAnAlreadyDeletedMessage() throws Exception {
        Message message = Message.text(roomId, memberId, "already gone", null);
        stampCreatedAt(message, OffsetDateTime.now());
        message.softDelete(memberId);
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.deleteMessage(memberId, AccountRole.INDIVIDUAL, messageId))
                .isInstanceOf(ConflictException.class);
    }

    // --- mute / read receipt ---------------------------------------------------------

    @Test
    void setMutedUpdatesOnlyTheCallersOwnParticipantRow() {
        ChatParticipant participant = ChatParticipant.create(roomId, memberId);
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(participant));

        chatService.setMuted(memberId, roomId, true);

        assertThat(participant.isMuted()).isTrue();
    }

    @Test
    void markReadUpdatesTheParticipantsLastReadPointer() {
        ChatParticipant participant = ChatParticipant.create(roomId, memberId);
        UUID lastMessageId = UUID.randomUUID();
        when(chatParticipantRepository.findByChatRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(participant));

        chatService.markRead(memberId, roomId, lastMessageId);

        assertThat(participant.getLastReadMessageId()).isEqualTo(lastMessageId);
        assertThat(participant.getLastReadAt()).isNotNull();
    }

    // --- helpers ----------------------------------------------------------------------

    private void stampCreatedAt(Message message, OffsetDateTime value) throws Exception {
        var field = Message.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(message, value);
    }
}
