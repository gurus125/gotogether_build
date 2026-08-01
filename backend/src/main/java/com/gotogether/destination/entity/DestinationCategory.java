package com.gotogether.destination.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code destination_category} enum (V1 migration). The
 * four values are exactly the category groups from the approved Create Trip
 * Flow design's Destination step ("Mountains", "Beaches", "Weekend Escapes",
 * "Adventure") — not invented, see V6 seed migration's own comment.
 */
public enum DestinationCategory {
    MOUNTAINS,
    BEACHES,
    WEEKEND_ESCAPES,
    ADVENTURE;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<DestinationCategory, String> {
        @Override
        public String convertToDatabaseColumn(DestinationCategory attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public DestinationCategory convertToEntityAttribute(String dbData) {
            return dbData == null ? null : DestinationCategory.valueOf(dbData.toUpperCase());
        }
    }
}
