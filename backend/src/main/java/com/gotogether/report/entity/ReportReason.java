package com.gotogether.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code report_reason} enum (V1 migration). */
public enum ReportReason {
    HARASSMENT,
    UNSAFE_BEHAVIOUR,
    FRAUD,
    FAKE_PROFILE,
    SPAM,
    INAPPROPRIATE_CONTENT,
    NO_SHOW,
    IDENTITY_MISMATCH,
    OTHER;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReportReason, String> {
        @Override
        public String convertToDatabaseColumn(ReportReason attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReportReason convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportReason.valueOf(dbData.toUpperCase());
        }
    }
}
