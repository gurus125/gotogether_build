package com.gotogether.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code rejection_reason} enum (V1 migration). */
public enum RejectionReason {
    BLURRY_IMAGE,
    NAME_MISMATCH,
    EXPIRED_DOCUMENT,
    SELFIE_MISMATCH,
    UNSUPPORTED_DOCUMENT_TYPE;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<RejectionReason, String> {
        @Override
        public String convertToDatabaseColumn(RejectionReason attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public RejectionReason convertToEntityAttribute(String dbData) {
            return dbData == null ? null : RejectionReason.valueOf(dbData.toUpperCase());
        }
    }
}
