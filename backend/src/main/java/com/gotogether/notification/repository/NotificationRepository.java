package com.gotogether.notification.repository;

import com.gotogether.notification.entity.Notification;
import com.gotogether.notification.entity.NotificationStatus;
import com.gotogether.notification.entity.NotificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * {@code GET /notifications}'s base query — excludes {@code ARCHIVED} so
     * that both {@code POST /notifications/read-all} ("Clear all") and the
     * per-row swipe-to-archive actually make a notification disappear from
     * the list, not just flip its unread dot off. (Originally this method
     * had no status filter at all, which was a real bug: archiving a row
     * server-side did nothing visible, since the very next fetch returned
     * that same archived row again.)
     */
    Page<Notification> findByRecipientIdAndStatusNotOrderByCreatedAtDesc(UUID recipientId, NotificationStatus excludedStatus, Pageable pageable);

    Page<Notification> findByRecipientIdAndTypeInAndStatusNotOrderByCreatedAtDesc(
            UUID recipientId, List<NotificationType> types, NotificationStatus excludedStatus, Pageable pageable);

    /**
     * {@code POST /notifications/read-all}'s candidates: "archives Read/Delivered, not queued."
     * Fetched and archived one-by-one in the service (entity method + save),
     * not a bulk {@code @Modifying} update — MVP per-user notification
     * volumes are small, and this keeps every status transition going
     * through {@link Notification#archive()} like every other entity in
     * this codebase, rather than a raw bulk statement bypassing it.
     */
    List<Notification> findByRecipientIdAndStatusIn(UUID recipientId, List<NotificationStatus> statuses);

    long countByRecipientIdAndStatus(UUID recipientId, NotificationStatus status);
}
