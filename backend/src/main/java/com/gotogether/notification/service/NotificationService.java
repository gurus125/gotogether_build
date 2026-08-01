package com.gotogether.notification.service;

import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.notification.dto.NotificationGroup;
import com.gotogether.notification.dto.NotificationPreferencesResponse;
import com.gotogether.notification.dto.NotificationResponse;
import com.gotogether.notification.dto.NotificationsPageResponse;
import com.gotogether.notification.dto.UpdateNotificationPreferencesRequest;
import com.gotogether.notification.entity.Notification;
import com.gotogether.notification.entity.NotificationPreferences;
import com.gotogether.notification.entity.NotificationStatus;
import com.gotogether.notification.entity.NotificationType;
import com.gotogether.notification.repository.NotificationPreferencesRepository;
import com.gotogether.notification.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The notification module's only entry point for other modules — everything
 * else ({@code notifications}/{@code notification_preferences} entities and
 * repositories) is package-private to this module in practice (enforced by
 * {@code ArchitectureTest}).
 *
 * <p>Unlike every other module built so far, {@code notification} has no
 * outbound dependencies of its own — it never needs to read
 * {@code trip}/{@code review}/{@code trust}/etc. back, so {@link #create} is
 * called <em>directly</em> from those modules' controllers ({@code
 * JoinRequestController}, {@code ChatController}, {@code TripController},
 * {@code MembershipController}, {@code ReviewController}) rather than
 * needing the controller-layer-composition-for-cycle-avoidance dance {@code
 * chat}/{@code trust} required. There is no cycle risk here in either
 * direction.
 *
 * <p><b>Real, wired triggers this pass</b> (Chapter 3 Section 3.9 /
 * Chapter 1 Section 18's Notification Center): a Join Request being
 * received/accepted/rejected, a new Chat message, a Trip being cancelled
 * (all members), a Trust Score changing (Review Published or Trip
 * Completed), and a Trip reaching Completed prompting members to review
 * each other.
 *
 * <p><b>Scoped down for this pass</b> (flagged here rather than silently
 * dropped, per this table's own {@code notification_type} enum naming more
 * types than are wired):
 * <ul>
 *   <li>{@code VERIFICATION_DECISION} — needs the Moderator ID-approval
 *   endpoint, which is Phase 8's {@code admin} module. The only verification
 *   transition reachable today (auto phone/email on signup) predates any
 *   notification-worthy event.
 *   <li>{@code DEPARTURE_REMINDER} — needs a scheduled job (no {@code
 *   @Scheduled} sweep infrastructure exists yet; Backend Architecture §12
 *   places this in a later polish phase).
 *   <li>{@code CHAT_MENTION} — Chat's {@code @mention} parsing itself was
 *   explicitly deferred in Phase 4 (see {@code ChatService}'s class doc).
 *   <li>{@code TRIP_UPDATE} for anything other than cancellation (e.g. an
 *   itinerary edit) — Trip Management/post-publish editing has no screen or
 *   endpoint yet.
 *   <li>{@code ANNOUNCEMENT} — Admin-only broadcast, needs the {@code admin}
 *   module (Phase 8).
 * </ul>
 *
 * <p><b>Real push delivery is not implemented.</b> {@link #create} writes a
 * row already {@link NotificationStatus#DELIVERED} — there is no Firebase
 * project wired, no device/push-token table in the schema, and no email
 * service, so the {@code Generated -> Queued -> Delivered|Failed} hop and
 * its retry logic (Chapter 3 Section 3.9) would have nothing real to
 * simulate against. In-app delivery is instant and is, per this table's own
 * migration comment, "the durable source of truth regardless of push
 * delivery success" anyway. {@code pushEnabled}/{@code emailEnabled}/{@code
 * marketingEnabled} preferences are stored and returned faithfully but are
 * currently inert for the same reason.
 */
@Service
public class NotificationService {

    private static final int DEFAULT_LIMIT = 20;

    /** Chapter 1 §18 / the approved Notifications screen's filter chips. */
    private static final Map<String, List<NotificationType>> FILTER_TYPES = Map.of(
            "trips", List.of(
                    NotificationType.JOIN_REQUEST_RECEIVED, NotificationType.JOIN_REQUEST_ACCEPTED,
                    NotificationType.JOIN_REQUEST_REJECTED, NotificationType.TRIP_UPDATE,
                    NotificationType.DEPARTURE_REMINDER, NotificationType.REVIEW_REMINDER,
                    NotificationType.ATTENDANCE_REMINDER),
            "chats", List.of(NotificationType.CHAT_MESSAGE, NotificationType.CHAT_MENTION),
            "trust", List.of(NotificationType.TRUST_UPDATE, NotificationType.VERIFICATION_DECISION));

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;

    public NotificationService(NotificationRepository notificationRepository, NotificationPreferencesRepository preferencesRepository) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
    }

    // --- cross-module entry point (called directly — see this class's doc) ---

    /**
     * Creates a notification for {@code recipientId}, unless they've turned
     * off in-app notifications entirely ({@code inAppEnabled = false}), or
     * (for the two reminder types) turned off reminders specifically.
     * {@code entityType} should be one of {@code common.ReferencedEntityType}'s
     * table names.
     *
     * <p>{@code type} is a {@code String} (one of {@link NotificationType}'s
     * constant names, e.g. {@code "CHAT_MESSAGE"}) rather than the enum
     * itself — {@code NotificationType} lives in this module's {@code
     * entity} package, and every calling controller here is in a
     * <em>different</em> module, so accepting the enum directly would be
     * exactly the cross-module entity access {@code ArchitectureTest}
     * forbids (same reason {@code review.entity.ReviewStatus} is never
     * passed around either — only ever exposed as a {@code String} on a DTO).
     */
    @Transactional
    public void create(
            UUID recipientId, UUID actorId, String type, String entityType, UUID entityId, String title,
            String body, String priority) {
        NotificationType parsedType = NotificationType.valueOf(type);
        NotificationPreferences prefs = ensurePreferences(recipientId);
        if (!prefs.isInAppEnabled()) {
            return;
        }
        boolean isReminder = parsedType == NotificationType.REVIEW_REMINDER || parsedType == NotificationType.DEPARTURE_REMINDER;
        if (isReminder && !prefs.isRemindersEnabled()) {
            return;
        }
        notificationRepository.save(Notification.create(recipientId, actorId, parsedType, entityType, entityId, title, body, priority));
    }

    // --- reads / mutations for the Notifications screen ------------------------

    /** {@code GET /notifications} (API Spec Section 13) — grouped into Today/Earlier This Week/Older (Chapter 1 §18), a header omitted when it has no items on this page. */
    @Transactional
    public NotificationsPageResponse list(UUID userId, String filter, String cursor, int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        int offset = OffsetCursor.decode(cursor);
        PageRequest pageRequest = PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit);

        List<NotificationType> types = filter == null ? null : FILTER_TYPES.get(filter);
        // Excludes ARCHIVED (see NotificationRepository's doc on the two
        // status-filtered finders) — otherwise "Clear all"/swipe-to-archive
        // archive the row server-side but it keeps reappearing here.
        Page<Notification> page = types == null
                ? notificationRepository.findByRecipientIdAndStatusNotOrderByCreatedAtDesc(userId, NotificationStatus.ARCHIVED, pageRequest)
                : notificationRepository.findByRecipientIdAndTypeInAndStatusNotOrderByCreatedAtDesc(userId, types, NotificationStatus.ARCHIVED, pageRequest);

        List<NotificationGroup> groups = groupByRecency(page.getContent());
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return new NotificationsPageResponse(groups, nextCursor, nextCursor != null);
    }

    /** {@code POST /notifications/{id}/read}. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .filter(existing -> existing.getRecipientId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        n.markRead();
        notificationRepository.save(n);
    }

    /**
     * {@code POST /notifications/{id}/archive} — the Notifications screen's
     * per-row swipe-to-archive (Chapter 1 §18 / mockup's "full left swipe
     * archives"). Silently a no-op if the row is already Archived (mirrors
     * {@code Notification.markRead}'s own no-op-if-already-terminal shape)
     * rather than throwing, since a double-swipe racing a slow response is a
     * plausible UI timing, not a real error.
     */
    @Transactional
    public void archive(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .filter(existing -> existing.getRecipientId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        if (n.isArchivable()) {
            n.archive();
            notificationRepository.save(n);
        }
    }

    /** {@code POST /notifications/read-all}: "archives Read/Delivered, not queued." */
    @Transactional
    public void markAllRead(UUID userId) {
        List<Notification> archivable = notificationRepository.findByRecipientIdAndStatusIn(
                userId, List.of(NotificationStatus.DELIVERED, NotificationStatus.READ));
        archivable.forEach(Notification::archive);
        notificationRepository.saveAll(archivable);
    }

    /** {@code GET /users/me/notification-preferences}. */
    @Transactional
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        return toResponse(ensurePreferences(userId));
    }

    /** {@code PATCH /users/me/notification-preferences}. */
    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId, UpdateNotificationPreferencesRequest request) {
        NotificationPreferences prefs = ensurePreferences(userId);
        prefs.apply(request.pushEnabled(), request.inAppEnabled(), request.emailEnabled(), request.marketingEnabled(), request.remindersEnabled());
        return toResponse(preferencesRepository.save(prefs));
    }

    // --- internal ---------------------------------------------------------------

    private NotificationPreferences ensurePreferences(UUID userId) {
        return preferencesRepository.findById(userId).orElseGet(() -> preferencesRepository.save(NotificationPreferences.defaultsFor(userId)));
    }

    private List<NotificationGroup> groupByRecency(List<Notification> notifications) {
        LocalDate today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        LocalDate weekStart = today.minusDays(6);

        List<NotificationResponse> todayItems = new ArrayList<>();
        List<NotificationResponse> earlierItems = new ArrayList<>();
        List<NotificationResponse> olderItems = new ArrayList<>();

        for (Notification n : notifications) {
            NotificationResponse response = toResponse(n);
            LocalDate createdDate = n.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
            if (createdDate.isEqual(today)) {
                todayItems.add(response);
            } else if (!createdDate.isBefore(weekStart)) {
                earlierItems.add(response);
            } else {
                olderItems.add(response);
            }
        }

        List<NotificationGroup> groups = new ArrayList<>();
        if (!todayItems.isEmpty()) groups.add(new NotificationGroup("TODAY", todayItems));
        if (!earlierItems.isEmpty()) groups.add(new NotificationGroup("EARLIER_THIS_WEEK", earlierItems));
        if (!olderItems.isEmpty()) groups.add(new NotificationGroup("OLDER", olderItems));
        return groups;
    }

    private NotificationResponse toResponse(Notification n) {
        boolean unread = n.getStatus() == NotificationStatus.DELIVERED;
        return new NotificationResponse(
                n.getId(), n.getActorId(), n.getType().name(), n.getEntityType(), n.getEntityId(), n.getTitle(), n.getBody(),
                n.getPriority(), n.getStatus().name(), unread, n.getCreatedAt());
    }

    private NotificationPreferencesResponse toResponse(NotificationPreferences p) {
        return new NotificationPreferencesResponse(
                p.isPushEnabled(), p.isInAppEnabled(), p.isEmailEnabled(), p.isMarketingEnabled(), p.isRemindersEnabled());
    }
}
