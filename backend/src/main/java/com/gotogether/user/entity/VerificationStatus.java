package com.gotogether.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code verification_status} enum (V1 migration). */
public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<VerificationStatus, String> {
        @Override
        public String convertToDatabaseColumn(VerificationStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public VerificationStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : VerificationStatus.valueOf(dbData.toUpperCase());
        }
    }
}
