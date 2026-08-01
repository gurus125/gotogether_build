package com.gotogether.analytics.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code analytics_event_type} enum (V1 migration) —
 * the fixed set of product events this codebase captures, feeding Business
 * Rules Operations Module D's Metric Set. Every category in that table maps
 * to one or more of these ten values; see {@code AnalyticsService}'s class
 * doc for exactly which Metric Set categories are actually computable from
 * this event set versus flagged as needing infrastructure that doesn't
 * exist yet (session tracking, guest-mode tracking, FCM push receipts).
 */
public enum AnalyticsEventType {
    TRIP_CREATED,
    TRIP_PUBLISHED,
    TRIP_JOINED,
    TRIP_COMPLETED,
    TRIP_CANCELLED,
    SEARCH_PERFORMED,
    REVIEW_SUBMITTED,
    TRUST_SCORE_UPDATED,
    VERIFICATION_APPROVED,
    NOTIFICATION_OPENED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<AnalyticsEventType, String> {
        @Override
        public String convertToDatabaseColumn(AnalyticsEventType attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public AnalyticsEventType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : AnalyticsEventType.valueOf(dbData.toUpperCase());
        }
    }
}
