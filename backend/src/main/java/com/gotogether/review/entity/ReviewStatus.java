package com.gotogether.review.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code review_status} enum (V1 migration) and Chapter
 * 3 Section 3.7's Review Lifecycle. Deliberately has no {@code PENDING} or
 * {@code EXPIRED} value — a {@code reviews} row is only ever inserted at the
 * moment of actual submission (Chapter 3's "Eligible"/"Pending"/"Expired"
 * states describe an <em>opportunity</em>, not a persisted row; "Expired"
 * with zero submissions simply means no row was ever created for that pair —
 * nothing to update).
 */
public enum ReviewStatus {
    SUBMITTED,
    PUBLISHED,
    HIDDEN,
    REMOVED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReviewStatus, String> {
        @Override
        public String convertToDatabaseColumn(ReviewStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReviewStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReviewStatus.valueOf(dbData.toUpperCase());
        }
    }
}
