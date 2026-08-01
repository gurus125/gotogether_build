package com.gotogether.notification.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code notification_status} enum (V1 migration) and
 * Chapter 3 Section 3.9's Notification Lifecycle: {@code Generated ->
 * Queued -> Delivered|Failed -> Read|Dismissed -> Archived} (Failed can
 * retry up to 3 times before also landing on Archived).
 *
 * <p>This pass only ever writes {@link #DELIVERED} at creation time — see
 * {@code NotificationService}'s class doc for why the {@code Generated}/
 * {@code Queued}/{@code Failed}/retry hops are skipped rather than faked
 * (no real push channel exists to queue against).
 */
public enum NotificationStatus {
    GENERATED,
    QUEUED,
    DELIVERED,
    READ,
    DISMISSED,
    ARCHIVED,
    FAILED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<NotificationStatus, String> {
        @Override
        public String convertToDatabaseColumn(NotificationStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public NotificationStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : NotificationStatus.valueOf(dbData.toUpperCase());
        }
    }
}
