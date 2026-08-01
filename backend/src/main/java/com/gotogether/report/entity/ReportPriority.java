package com.gotogether.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code report_priority} enum (V1 migration). Also
 * the Moderator triage queue's own sort order (Operations Module B's
 * Moderator Workflow: "emergency reports first, then identity-misuse/safety
 * reports, then routine content reports"), by declaration order — see
 * {@code ReportRepository}'s query for how that's expressed as a case-based
 * ORDER BY rather than relying on enum ordinal alone.
 */
public enum ReportPriority {
    EMERGENCY,
    SAFETY,
    ROUTINE;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReportPriority, String> {
        @Override
        public String convertToDatabaseColumn(ReportPriority attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReportPriority convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportPriority.valueOf(dbData.toUpperCase());
        }
    }
}
