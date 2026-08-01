package com.gotogether.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code verification_level} enum (V1 migration) — a
 * denormalized cache of the highest passed {@link Verification} for cheap
 * permission checks (users.verification_level, DB Schema Part 1 Section 3).
 */
public enum VerificationLevel {
    NONE,
    PHONE,
    EMAIL,
    ID_APPROVED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<VerificationLevel, String> {
        @Override
        public String convertToDatabaseColumn(VerificationLevel attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public VerificationLevel convertToEntityAttribute(String dbData) {
            return dbData == null ? null : VerificationLevel.valueOf(dbData.toUpperCase());
        }
    }
}
