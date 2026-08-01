package com.gotogether.notification.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code notification_type} enum (V1 migration).
 *
 * <p>Only a subset is actually created by this pass — see {@code
 * NotificationService}'s class doc for exactly which types have a real
 * trigger wired versus which are flagged as deferred (no backing feature
 * exists yet for those).
 */
public enum NotificationType {
    JOIN_REQUEST_RECEIVED,
    JOIN_REQUEST_ACCEPTED,
    JOIN_REQUEST_REJECTED,
    CHAT_MESSAGE,
    CHAT_MENTION,
    TRIP_UPDATE,
    DEPARTURE_REMINDER,
    REVIEW_REMINDER,
    VERIFICATION_DECISION,
    TRUST_UPDATE,
    ANNOUNCEMENT,
    /** Organizer-only, sent when a trip reaches Completed — prompts marking each member's attendance (V9 migration). See {@code TripLifecycleScheduler}. */
    ATTENDANCE_REMINDER;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<NotificationType, String> {
        @Override
        public String convertToDatabaseColumn(NotificationType attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public NotificationType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : NotificationType.valueOf(dbData.toUpperCase());
        }
    }
}
