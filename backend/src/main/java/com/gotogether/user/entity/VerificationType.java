package com.gotogether.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code verification_type} enum (V1 migration). */
public enum VerificationType {
    PHONE,
    EMAIL,
    GOVERNMENT_ID,
    SELFIE_MATCH;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<VerificationType, String> {
        @Override
        public String convertToDatabaseColumn(VerificationType attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public VerificationType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : VerificationType.valueOf(dbData.toUpperCase());
        }
    }
}
