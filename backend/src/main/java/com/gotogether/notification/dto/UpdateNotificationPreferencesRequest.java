package com.gotogether.notification.dto;

/** {@code PATCH /users/me/notification-preferences} — PATCH semantics, only non-null fields are applied (matches {@code profile.dto.UpdateProfileRequest}'s convention). */
public record UpdateNotificationPreferencesRequest(
        Boolean pushEnabled, Boolean inAppEnabled, Boolean emailEnabled, Boolean marketingEnabled, Boolean remindersEnabled) {
}
