package com.gotogether.profile.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code profile_visibility} enum. Single value
 * ({@code PUBLIC}) at MVP per Chapter 2 Section 2.3 — the column is reserved
 * for future granularity, not dead weight.
 */
public enum ProfileVisibility {
    PUBLIC;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ProfileVisibility, String> {
        @Override
        public String convertToDatabaseColumn(ProfileVisibility attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ProfileVisibility convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ProfileVisibility.valueOf(dbData.toUpperCase());
        }
    }
}
