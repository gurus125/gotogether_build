package com.gotogether.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.notification.dto.NotificationsPageResponse;
import com.gotogether.notification.entity.Notification;
import com.gotogether.notification.entity.NotificationPreferences;
import com.gotogether.notification.entity.NotificationStatus;
import com.gotogether.notification.entity.NotificationType;
import com.gotogether.notification.repository.NotificationPreferencesRepository;
import com.gotogether.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferencesRepository preferencesRepository;

    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, preferencesRepository);
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(preferencesRepository.save(any(NotificationPreferences.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createSeedsDefaultPreferencesAndSavesWhenNoRowExistsYet() {
        when(preferencesRepository.findById(userId)).thenReturn(Optional.empty());

        notificationService.create(userId, actorId, "CHAT_MESSAGE", "messages", UUID.randomUUID(), "New message", "Jordan: hi", "low");

        verify(preferencesRepository).save(any(NotificationPreferences.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void createSkipsEntirelyWhenInAppNotificationsAreDisabled() {
        NotificationPreferences prefs = NotificationPreferences.defaultsFor(userId);
        prefs.apply(null, false, null, null, null);
        when(preferencesRepository.findById(userId)).thenReturn(Optional.of(prefs));

        notificationService.create(userId, actorId, "CHAT_MESSAGE", "messages", UUID.randomUUID(), "New message", "hi", "low");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createSkipsAReviewReminderWhenRemindersAreDisabledButOtherTypesStillGoThrough() {
        NotificationPreferences prefs = NotificationPreferences.defaultsFor(userId);
        prefs.apply(null, null, null, null, false);
        when(preferencesRepository.findById(userId)).thenReturn(Optional.of(prefs));

        notificationService.create(userId, actorId, "REVIEW_REMINDER", "trips", UUID.randomUUID(), "Rate your trip", "body", "medium");
        verify(notificationRepository, never()).save(any());

        notificationService.create(userId, actorId, "TRUST_UPDATE", "trips", UUID.randomUUID(), "Trust updated", "body", "low");
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void createdNotificationIsAlreadyDeliveredNoRealPushChannelToQueueAgainst() {
        when(preferencesRepository.findById(userId)).thenReturn(Optional.of(NotificationPreferences.defaultsFor(userId)));
        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);

        notificationService.create(userId, actorId, "CHAT_MESSAGE", "messages", UUID.randomUUID(), "New message", "hi", "low");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void markReadThrowsWhenTheNotificationBelongsToSomeoneElse() {
        UUID notificationId = UUID.randomUUID();
        Notification n = Notification.create(UUID.randomUUID(), actorId, NotificationType.CHAT_MESSAGE, "messages", UUID.randomUUID(), "t", "b", "low");
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markRead(userId, notificationId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markReadTransitionsDeliveredToRead() {
        UUID notificationId = UUID.randomUUID();
        Notification n = Notification.create(userId, actorId, NotificationType.CHAT_MESSAGE, "messages", UUID.randomUUID(), "t", "b", "low");
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        notificationService.markRead(userId, notificationId);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    void archiveThrowsWhenTheNotificationBelongsToSomeoneElse() {
        UUID notificationId = UUID.randomUUID();
        Notification n = Notification.create(UUID.randomUUID(), actorId, NotificationType.CHAT_MESSAGE, "messages", UUID.randomUUID(), "t", "b", "low");
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.archive(userId, notificationId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void archiveTransitionsDeliveredToArchived() {
        UUID notificationId = UUID.randomUUID();
        Notification n = Notification.create(userId, actorId, NotificationType.CHAT_MESSAGE, "messages", UUID.randomUUID(), "t", "b", "low");
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(n));

        notificationService.archive(userId, notificationId);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.ARCHIVED);
        verify(notificationRepository).save(n);
    }

    @Test
    void markAllReadArchivesOnlyDeliveredAndReadRows() {
        Notification delivered = Notification.create(userId, actorId, NotificationType.CHAT_MESSAGE, "messages", UUID.randomUUID(), "t", "b", "low");
        Notification read = Notification.create(userId, actorId, NotificationType.TRUST_UPDATE, "trips", UUID.randomUUID(), "t2", "b2", "low");
        read.markRead();
        when(notificationRepository.findByRecipientIdAndStatusIn(userId, List.of(NotificationStatus.DELIVERED, NotificationStatus.READ)))
                .thenReturn(List.of(delivered, read));

        notificationService.markAllRead(userId);

        assertThat(delivered.getStatus()).isEqualTo(NotificationStatus.ARCHIVED);
        assertThat(read.getStatus()).isEqualTo(NotificationStatus.ARCHIVED);
        verify(notificationRepository).saveAll(List.of(delivered, read));
    }

    @Test
    void listGroupsNotificationsIntoTodayAndOlderOmittingEmptySections() {
        Notification todayOne = Notification.create(userId, actorId, NotificationType.CHAT_MESSAGE, "messages", UUID.randomUUID(), "Today item", "b", "low");
        Page<Notification> page = new PageImpl<>(List.of(todayOne));
        when(notificationRepository.findByRecipientIdAndStatusNotOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq(NotificationStatus.ARCHIVED), any()))
                .thenReturn(page);

        NotificationsPageResponse response = notificationService.list(userId, "all", null, 20);

        assertThat(response.groups()).hasSize(1);
        assertThat(response.groups().get(0).label()).isEqualTo("TODAY");
        assertThat(response.groups().get(0).items()).hasSize(1);
        assertThat(response.groups().get(0).items().get(0).unread()).isTrue();
    }

    @Test
    void listExcludesArchivedNotificationsSoClearAllAndSwipeToArchiveActuallyRemoveThemFromView() {
        when(notificationRepository.findByRecipientIdAndStatusNotOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq(NotificationStatus.ARCHIVED), any()))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.list(userId, "all", null, 20);

        verify(notificationRepository).findByRecipientIdAndStatusNotOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq(NotificationStatus.ARCHIVED), any());
    }

    @Test
    void getPreferencesLazilySeedsDefaultsForABrandNewUser() {
        when(preferencesRepository.findById(userId)).thenReturn(Optional.empty());

        var response = notificationService.getPreferences(userId);

        assertThat(response.pushEnabled()).isTrue();
        assertThat(response.inAppEnabled()).isTrue();
        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.remindersEnabled()).isTrue();
    }
}
