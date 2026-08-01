package com.gotogether.notification.dto;

/** Mirrors {@code notification.entity.NotificationPreferences} — {@code GET/PATCH /users/me/notification-preferences} (API Spec Section 13). */
public record NotificationPreferencesResponse(
        boolean pushEnabled, boolean inAppEnabled, boolean emailEnabled, boolean marketingEnabled, boolean remindersEnabled) {
}
