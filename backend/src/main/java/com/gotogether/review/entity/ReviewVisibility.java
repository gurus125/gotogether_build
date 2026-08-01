package com.gotogether.review.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code review_visibility} enum (V1 migration) — the
 * double-blind gate (Trust & Discovery Module B), distinct from {@link
 * ReviewStatus}: a review can be {@code status=SUBMITTED} while {@code
 * visibility=BLIND} (invisible to its subject and everyone else until both
 * sides submit or the window closes). No {@code REMOVED} value here — once a
 * review is hidden it simply stays {@code HIDDEN} regardless of whether
 * {@code status} later moves to {@code REMOVED} (permanent); there's nothing
 * further to distinguish at the visibility level.
 */
public enum ReviewVisibility {
    BLIND,
    PUBLISHED,
    HIDDEN;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReviewVisibility, String> {
        @Override
        public String convertToDatabaseColumn(ReviewVisibility attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReviewVisibility convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReviewVisibility.valueOf(dbData.toUpperCase());
        }
    }
}
