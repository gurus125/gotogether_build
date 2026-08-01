package com.gotogether.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-user delivery-channel configuration (DB Schema Part 2) — {@code
 * GET/PATCH /users/me/notification-preferences} (API Spec Section 13).
 * Same shape decision as {@code trust.entity.TrustScore}: does not extend
 * {@link com.gotogether.common.entity.BaseEntity}/{@code AuditableEntity}
 * since {@code notification_preferences.user_id} is simultaneously the PK
 * and FK to {@code users} (true 1:1 owned-key), so there's no separate
 * app-generated {@code id}.
 *
 * <p>Only {@code inAppEnabled} and {@code remindersEnabled} have any real
 * effect this pass (see {@code NotificationService}'s class doc) —
 * {@code pushEnabled}/{@code emailEnabled}/{@code marketingEnabled} are
 * stored and returned faithfully (the API contract exists) but are
 * functionally inert: no FCM project, no email-sending service, and no
 * device-token table exist yet to act on them.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "marketing_enabled", nullable = false)
    private boolean marketingEnabled = false;

    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected NotificationPreferences() {
        // JPA
    }

    public static NotificationPreferences defaultsFor(UUID userId) {
        NotificationPreferences p = new NotificationPreferences();
        p.userId = userId;
        return p;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isMarketingEnabled() {
        return marketingEnabled;
    }

    public boolean isRemindersEnabled() {
        return remindersEnabled;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void apply(Boolean pushEnabled, Boolean inAppEnabled, Boolean emailEnabled, Boolean marketingEnabled, Boolean remindersEnabled) {
        if (pushEnabled != null) this.pushEnabled = pushEnabled;
        if (inAppEnabled != null) this.inAppEnabled = inAppEnabled;
        if (emailEnabled != null) this.emailEnabled = emailEnabled;
        if (marketingEnabled != null) this.marketingEnabled = marketingEnabled;
        if (remindersEnabled != null) this.remindersEnabled = remindersEnabled;
    }
}
