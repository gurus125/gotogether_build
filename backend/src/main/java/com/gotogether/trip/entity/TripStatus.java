package com.gotogether.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code trip_status} enum (V1 migration) and Chapter
 * 3 Section 3.2's trip lifecycle state machine exactly:
 * {@code Draft -> Published -> AcceptingRequests -> Confirmed -> Full ->
 * InProgress -> Completed}, with {@code Cancelled} a terminal branch from
 * any non-terminal state.
 *
 * <p>Phase 2 (this module, before {@code joinrequest}/{@code membership}
 * exist) only drives {@code DRAFT -> PUBLISHED} and {@code -> CANCELLED};
 * the remaining transitions ({@code ACCEPTING_REQUESTS} on first join
 * request, {@code CONFIRMED}/{@code FULL} on group-size thresholds,
 * {@code IN_PROGRESS}/{@code COMPLETED} on scheduled jobs) are Phase 3+
 * concerns and are modeled here only so the column round-trips correctly —
 * see {@code TripService}'s class doc for exactly which transitions Phase 2
 * implements.
 */
public enum TripStatus {
    DRAFT,
    PUBLISHED,
    ACCEPTING_REQUESTS,
    CONFIRMED,
    FULL,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ARCHIVED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<TripStatus, String> {
        @Override
        public String convertToDatabaseColumn(TripStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public TripStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : TripStatus.valueOf(dbData.toUpperCase());
        }
    }
}
