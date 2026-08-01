package com.gotogether.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code report_status} enum (V1 migration) — Business Rules Operations Module B's Moderator Workflow. */
public enum ReportStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    DISMISSED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReportStatus, String> {
        @Override
        public String convertToDatabaseColumn(ReportStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReportStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportStatus.valueOf(dbData.toUpperCase());
        }
    }
}
